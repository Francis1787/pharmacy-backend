package com.app.pharmacy.service.impl;

import com.app.pharmacy.domain.dtos.common.RefSummary;
import com.app.pharmacy.domain.dtos.request.SaleCreateRequest;
import com.app.pharmacy.domain.dtos.response.SaleItemResponse;
import com.app.pharmacy.domain.dtos.response.SaleResponse;
import com.app.pharmacy.domain.entity.*;
import com.app.pharmacy.domain.entity.enums.ActionType;
import com.app.pharmacy.domain.entity.enums.ApprovalStatus;
import com.app.pharmacy.domain.entity.enums.PaymentMethod;
import com.app.pharmacy.domain.entity.enums.StaffRole;
import com.app.pharmacy.exception.BusinessRuleViolationException;
import com.app.pharmacy.exception.ResourceNotFoundException;
import com.app.pharmacy.repository.*;
import com.app.pharmacy.service.AuditLogService;
import com.app.pharmacy.service.SaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SaleServiceImpl implements SaleService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final BatchRepository batchRepository;
    private final StaffRepository staffRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public SaleResponse createSale(SaleCreateRequest request, UUID authenticatedStaffId) {
        Prescription prescription = prescriptionRepository.findById(request.prescriptionId())
                .orElseThrow(() -> new ResourceNotFoundException("Prescription not found: " + request.prescriptionId()));

        // Rule 1: no dispensing without an approved prescription.
        if (prescription.getApprovalStatus() != ApprovalStatus.Approved) {
            throw new BusinessRuleViolationException(
                    "Prescription " + prescription.getId() + " is " + prescription.getApprovalStatus()
                            + " — only an Approved prescription can be dispensed (Rule 1)");
        }

        // Sale.prescription_id is UNIQUE — a prescription can only be fulfilled once.
        if (saleRepository.existsByPrescriptionId(prescription.getId())) {
            throw new BusinessRuleViolationException(
                    "Prescription " + prescription.getId() + " has already been dispensed");
        }

        // Rule 15: only a Pharmacist may complete a sale, whether one handles both
        // roles or two split cashier/dispensing between them.
        UUID cashierId = request.cashierId() != null ? request.cashierId() : authenticatedStaffId;
        UUID dispensingPharmacistId = request.dispensingPharmacistId() != null ? request.dispensingPharmacistId() : authenticatedStaffId;
        Staff cashier = getPharmacistOrThrow(cashierId);
        Staff dispensingPharmacist = getPharmacistOrThrow(dispensingPharmacistId);

        Sale sale = Sale.builder()
                .prescription(prescription)
                .cashier(cashier)
                .dispensingPharmacist(dispensingPharmacist)
                .saleDate(LocalDateTime.now())
                .totalAmount(BigDecimal.ZERO) // finalized below once items are priced
                .paymentMethod(request.paymentMethod().dbValue())
                .build();
        sale = saleRepository.save(sale);

        BigDecimal total = BigDecimal.ZERO;
        boolean touchedControlledSubstance = false;

        for (SaleCreateRequest.Item itemReq : request.items()) {
            Batch batch = batchRepository.findById(itemReq.batchId())
                    .orElseThrow(() -> new ResourceNotFoundException("Batch not found: " + itemReq.batchId()));

            // Rule 5: a batch past its expiry date cannot be dispensed or sold, no exceptions.
            if (batch.isExpired()) {
                throw new BusinessRuleViolationException(
                        "Batch " + batch.getId() + " (" + batch.getBatchNumber() + ") expired on "
                                + batch.getExpiryDate() + " and cannot be sold (Rule 5)");
            }

            if (batch.getQuantityInStock() < itemReq.quantitySold()) {
                throw new BusinessRuleViolationException(
                        "Insufficient stock in batch " + batch.getBatchNumber() + ": requested "
                                + itemReq.quantitySold() + ", available " + batch.getQuantityInStock());
            }

            BigDecimal unitPriceAtSale = batch.getDrug().getUnitPrice();

            // Rule 4: stock is decremented automatically at the moment of dispensing, never manually.
            batch.setQuantityInStock(batch.getQuantityInStock() - itemReq.quantitySold());
            batchRepository.save(batch);

            SaleItem saleItem = SaleItem.builder()
                    .sale(sale)
                    .batch(batch)
                    .quantitySold(itemReq.quantitySold())
                    .unitPriceAtSale(unitPriceAtSale)
                    .build();
            saleItemRepository.save(saleItem);

            total = total.add(unitPriceAtSale.multiply(BigDecimal.valueOf(itemReq.quantitySold())));

            if (Boolean.TRUE.equals(batch.getDrug().getIsControlledSubstance())) {
                touchedControlledSubstance = true;
            }
        }

        sale.setTotalAmount(total);
        sale = saleRepository.save(sale);

        // Rule 3: controlled-substance transactions carry extra mandatory logging,
        // even though only the one dispensing Pharmacist's approval is required.
        String note = touchedControlledSubstance
                ? "Dispensed sale including controlled substance item(s) — single-pharmacist approval per Rule 3"
                : "Dispensed sale";
        auditLogService.logAction(dispensingPharmacistId, ActionType.DRUG_DISPENSED, sale.getId(), "Sale", note);

        return toResponse(sale);
    }

    @Override
    public SaleResponse getSaleById(UUID id) {
        return toResponse(getSaleOrThrow(id));
    }

    @Override
    public List<SaleResponse> getAllSales() {
        return saleRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    public List<SaleResponse> getSalesByDateRange(LocalDateTime from, LocalDateTime to) {
        return saleRepository.findBySaleDateBetween(from, to).stream().map(this::toResponse).toList();
    }

    @Override
    public List<SaleResponse> getSalesByDispensingPharmacist(UUID pharmacistId) {
        return saleRepository.findByDispensingPharmacistId(pharmacistId).stream().map(this::toResponse).toList();
    }

    private Sale getSaleOrThrow(UUID id) {
        return saleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found: " + id));
    }

    private Staff getPharmacistOrThrow(UUID staffId) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found: " + staffId));
        if (staff.getRole() != StaffRole.Pharmacist) {
            throw new BusinessRuleViolationException(
                    "Only a Pharmacist may take payment or dispense a sale (Rule 15) — " + staff.getFullName() + " is " + staff.getRole());
        }
        return staff;
    }

    private SaleResponse toResponse(Sale sale) {
        List<SaleItemResponse> items = saleItemRepository.findBySaleId(sale.getId())
                .stream()
                .map(item -> new SaleItemResponse(
                        item.getId(),
                        new RefSummary(item.getBatch().getId(), item.getBatch().getBatchNumber()),
                        new RefSummary(item.getBatch().getDrug().getId(), item.getBatch().getDrug().getName()),
                        item.getQuantitySold(),
                        item.getUnitPriceAtSale()
                ))
                .collect(Collectors.toList());

        return new SaleResponse(
                sale.getId(),
                new RefSummary(sale.getPrescription().getId(), sale.getPrescription().getId().toString()),
                new RefSummary(sale.getCashier().getId(), sale.getCashier().getFullName()),
                new RefSummary(sale.getDispensingPharmacist().getId(), sale.getDispensingPharmacist().getFullName()),
                sale.getSaleDate(),
                sale.getTotalAmount(),
                PaymentMethod.fromDbValue(sale.getPaymentMethod()),
                items
        );
    }
}

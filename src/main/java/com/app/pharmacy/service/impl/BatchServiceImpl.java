package com.app.pharmacy.service.impl;

import com.app.pharmacy.domain.dtos.common.RefSummary;
import com.app.pharmacy.domain.dtos.request.BatchCreateRequest;
import com.app.pharmacy.domain.dtos.response.BatchResponse;
import com.app.pharmacy.domain.entity.*;
import com.app.pharmacy.domain.entity.enums.ActionType;
import com.app.pharmacy.domain.entity.enums.StaffRole;
import com.app.pharmacy.exception.BusinessRuleViolationException;
import com.app.pharmacy.exception.ResourceNotFoundException;
import com.app.pharmacy.repository.*;
import com.app.pharmacy.service.AuditLogService;
import com.app.pharmacy.service.BatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BatchServiceImpl implements BatchService {

    private final BatchRepository batchRepository;
    private final DrugRepository drugRepository;
    private final SupplierRepository supplierRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final StaffRepository staffRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public BatchResponse createBatch(BatchCreateRequest request) {
        Drug drug = drugRepository.findById(request.drugId())
                .orElseThrow(() -> new ResourceNotFoundException("Drug not found: " + request.drugId()));
        Supplier supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + request.supplierId()));

        PurchaseOrderItem poItem = null;
        if (request.purchaseOrderItemId() != null) {
            poItem = purchaseOrderItemRepository.findById(request.purchaseOrderItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Purchase order item not found: " + request.purchaseOrderItemId()));
        }

        Batch batch = Batch.builder()
                .drug(drug)
                .batchNumber(request.batchNumber())
                .quantityInStock(request.quantityInStock())
                .expiryDate(request.expiryDate())
                .dateReceived(LocalDate.now())
                .supplier(supplier)
                .purchaseOrderItem(poItem)
                .verifiedByPharmacist(null) // controlled substances require a separate verify() call (Rule 11)
                .build();

        return toResponse(batchRepository.save(batch));
    }

    @Override
    @Transactional
    public BatchResponse verifyBatch(UUID batchId, UUID pharmacistId) {
        Batch batch = getBatchOrThrow(batchId);

        Staff pharmacist = staffRepository.findById(pharmacistId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found: " + pharmacistId));
        if (pharmacist.getRole() != StaffRole.Pharmacist) {
            throw new BusinessRuleViolationException("Only a Pharmacist may verify a delivered batch (Rule 11)");
        }

        batch.setVerifiedByPharmacist(pharmacist);
        Batch saved = batchRepository.save(batch);

        String note = Boolean.TRUE.equals(batch.getDrug().getIsControlledSubstance())
                ? "Verified controlled-substance delivery matches purchase order"
                : "Verified delivery matches purchase order";
        auditLogService.logAction(pharmacistId, ActionType.STOCK_UPDATED, batch.getId(), "Batch", note);

        return toResponse(saved);
    }

    @Override
    public BatchResponse getBatchById(UUID id) {
        return toResponse(getBatchOrThrow(id));
    }

    @Override
    public List<BatchResponse> getAllBatches() {
        return batchRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    public List<BatchResponse> getBatchesByDrug(UUID drugId) {
        return batchRepository.findByDrugId(drugId).stream().map(this::toResponse).toList();
    }

    @Override
    public List<BatchResponse> getBatchesExpiringWithinDays(int days) {
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(days);
        return batchRepository.findByExpiryDateBetween(from, to).stream().map(this::toResponse).toList();
    }

    private Batch getBatchOrThrow(UUID id) {
        return batchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Batch not found: " + id));
    }

    private BatchResponse toResponse(Batch batch) {
        return new BatchResponse(
                batch.getId(),
                new RefSummary(batch.getDrug().getId(), batch.getDrug().getName()),
                batch.getBatchNumber(),
                batch.getQuantityInStock(),
                batch.getExpiryDate(),
                batch.isExpired(),
                batch.getDateReceived(),
                new RefSummary(batch.getSupplier().getId(), batch.getSupplier().getCompanyName()),
                batch.getPurchaseOrderItem() != null ? batch.getPurchaseOrderItem().getId() : null,
                batch.getVerifiedByPharmacist() != null
                        ? new RefSummary(batch.getVerifiedByPharmacist().getId(), batch.getVerifiedByPharmacist().getFullName())
                        : null,
                batch.getDrug().getIsControlledSubstance()
        );
    }
}

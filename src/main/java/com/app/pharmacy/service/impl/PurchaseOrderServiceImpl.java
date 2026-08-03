package com.app.pharmacy.service.impl;

import com.app.pharmacy.domain.dtos.common.RefSummary;
import com.app.pharmacy.domain.dtos.request.MarkDeliveredRequest;
import com.app.pharmacy.domain.dtos.request.PurchaseOrderCreateRequest;
import com.app.pharmacy.domain.dtos.response.PurchaseOrderItemResponse;
import com.app.pharmacy.domain.dtos.response.PurchaseOrderResponse;
import com.app.pharmacy.domain.entity.*;
import com.app.pharmacy.domain.entity.enums.ActionType;
import com.app.pharmacy.domain.entity.enums.PurchaseOrderStatus;
import com.app.pharmacy.exception.ResourceNotFoundException;
import com.app.pharmacy.repository.*;
import com.app.pharmacy.service.AuditLogService;
import com.app.pharmacy.service.PurchaseOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final SupplierRepository supplierRepository;
    private final DrugRepository drugRepository;
    private final StaffRepository staffRepository;
    private final BatchRepository batchRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public PurchaseOrderResponse createPurchaseOrder(PurchaseOrderCreateRequest request, UUID createdByStaffId) {
        Supplier supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + request.supplierId()));
        Staff createdBy = staffRepository.findById(createdByStaffId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found: " + createdByStaffId));

        PurchaseOrder order = PurchaseOrder.builder()
                .supplier(supplier)
                .orderDate(LocalDate.now())
                .expectedDeliveryDate(request.expectedDeliveryDate())
                .status(PurchaseOrderStatus.Pending)
                .createdBy(createdBy)
                .build();
        order = purchaseOrderRepository.save(order);

        for (PurchaseOrderCreateRequest.Item itemReq : request.items()) {
            Drug drug = drugRepository.findById(itemReq.drugId())
                    .orElseThrow(() -> new ResourceNotFoundException("Drug not found: " + itemReq.drugId()));

            PurchaseOrderItem item = PurchaseOrderItem.builder()
                    .purchaseOrder(order)
                    .drug(drug)
                    .quantityOrdered(itemReq.quantityOrdered())
                    .unitCost(itemReq.unitCost())
                    .build();
            purchaseOrderItemRepository.save(item);
        }

        auditLogService.logAction(createdByStaffId, ActionType.PURCHASE_ORDER_CREATED, order.getId(), "PurchaseOrder",
                "Created purchase order with " + request.items().size() + " line item(s)");

        return toResponse(order);
    }

    @Override
    @Transactional
    public PurchaseOrderResponse markDelivered(UUID id, MarkDeliveredRequest request) {
        PurchaseOrder order = getOrderOrThrow(id);
        order.setActualDeliveryDate(request.actualDeliveryDate());
        order.setStatus(PurchaseOrderStatus.Received);
        return toResponse(purchaseOrderRepository.save(order));
    }

    @Override
    public PurchaseOrderResponse getPurchaseOrderById(UUID id) {
        return toResponse(getOrderOrThrow(id));
    }

    @Override
    public List<PurchaseOrderResponse> getAllPurchaseOrders() {
        return purchaseOrderRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    public List<PurchaseOrderResponse> getOverduePurchaseOrders() {
        return purchaseOrderRepository
                .findByActualDeliveryDateIsNullAndExpectedDeliveryDateBeforeAndStatusNot(
                        LocalDate.now(), PurchaseOrderStatus.Cancelled)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public List<PurchaseOrderResponse> getPurchaseOrdersAwaitingVerification() {
        List<Batch> unverified = batchRepository.findUnverifiedControlledSubstanceBatches();

        return unverified.stream()
                .map(batch -> batch.getPurchaseOrderItem().getPurchaseOrder())
                .distinct()
                .sorted(Comparator.comparing(PurchaseOrder::getOrderDate))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private PurchaseOrder getOrderOrThrow(UUID id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found: " + id));
    }

    private PurchaseOrderResponse toResponse(PurchaseOrder order) {
        List<PurchaseOrderItemResponse> items = purchaseOrderItemRepository.findByPurchaseOrderId(order.getId())
                .stream()
                .map(item -> new PurchaseOrderItemResponse(
                        item.getId(),
                        new RefSummary(item.getDrug().getId(), item.getDrug().getName()),
                        item.getQuantityOrdered(),
                        item.getUnitCost()
                ))
                .toList();

        boolean isOverdue = order.getActualDeliveryDate() == null
                && order.getExpectedDeliveryDate() != null
                && order.getExpectedDeliveryDate().isBefore(LocalDate.now())
                && order.getStatus() != PurchaseOrderStatus.Cancelled;

        return new PurchaseOrderResponse(
                order.getId(),
                new RefSummary(order.getSupplier().getId(), order.getSupplier().getCompanyName()),
                order.getOrderDate(),
                order.getExpectedDeliveryDate(),
                order.getActualDeliveryDate(),
                order.getStatus(),
                new RefSummary(order.getCreatedBy().getId(), order.getCreatedBy().getFullName()),
                isOverdue,
                items
        );
    }
}

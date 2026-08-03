package com.app.pharmacy.domain.dtos.response;

import com.app.pharmacy.domain.dtos.common.RefSummary;
import com.app.pharmacy.domain.entity.enums.PurchaseOrderStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * isOverdue is a computed convenience flag: true when actualDeliveryDate
 * is null and expectedDeliveryDate has already passed (Rule 17) — saves
 * the frontend from having to recompute that comparison itself.
 */
public record PurchaseOrderResponse(
        UUID id,
        RefSummary supplier,
        LocalDate orderDate,
        LocalDate expectedDeliveryDate,
        LocalDate actualDeliveryDate,
        PurchaseOrderStatus status,
        RefSummary createdBy,
        boolean isOverdue,
        List<PurchaseOrderItemResponse> items
) {
}

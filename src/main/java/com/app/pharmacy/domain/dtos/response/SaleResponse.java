package com.app.pharmacy.domain.dtos.response;

import com.app.pharmacy.domain.dtos.common.RefSummary;
import com.app.pharmacy.domain.entity.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Doubles as the receipt view for GET /sales/{id}. */
public record SaleResponse(
        UUID id,
        RefSummary prescription,
        RefSummary cashier,
        RefSummary dispensingPharmacist,
        LocalDateTime saleDate,
        BigDecimal totalAmount,
        PaymentMethod paymentMethod,
        List<SaleItemResponse> items
) {
}

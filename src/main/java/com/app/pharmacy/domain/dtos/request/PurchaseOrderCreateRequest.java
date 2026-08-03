package com.app.pharmacy.domain.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Admin only (Rule 16). Line items are created as part of this request —
 * PurchaseOrderItem has no standalone POST endpoint.
 */
public record PurchaseOrderCreateRequest(
        @NotNull UUID supplierId,

        @FutureOrPresent(message = "expectedDeliveryDate cannot be in the past")
        LocalDate expectedDeliveryDate,

        @NotEmpty @Valid
        List<Item> items
) {
    public record Item(
            @NotNull UUID drugId,
            @Min(1) int quantityOrdered,
            @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal unitCost
    ) {
    }
}

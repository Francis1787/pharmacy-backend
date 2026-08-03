package com.app.pharmacy.domain.dtos.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Pharmacist or Technician (Rule 11). purchaseOrderItemId is nullable —
 * not every batch traces back to a formal order (e.g. manual correction).
 * The service layer, not this DTO, enforces that a controlled-substance
 * batch must eventually be verified (PATCH /batches/{id}/verify) before
 * it counts as sellable stock.
 */
public record BatchCreateRequest(
        @NotNull UUID drugId,
        @NotBlank String batchNumber,
        @Min(0) int quantityInStock,

        @NotNull @Future(message = "expiryDate must be in the future")
        LocalDate expiryDate,

        @NotNull UUID supplierId,
        UUID purchaseOrderItemId
) {
}

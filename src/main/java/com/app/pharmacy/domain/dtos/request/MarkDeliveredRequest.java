package com.app.pharmacy.domain.dtos.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;

/** PATCH /purchase-orders/{id}/mark-delivered — Admin only. Sets actual_delivery_date. */
public record MarkDeliveredRequest(
        @NotNull @PastOrPresent(message = "actualDeliveryDate cannot be in the future")
        LocalDate actualDeliveryDate
) {
}

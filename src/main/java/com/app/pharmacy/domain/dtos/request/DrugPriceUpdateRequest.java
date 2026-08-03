package com.app.pharmacy.domain.dtos.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** PATCH /drugs/{id}/price — Admin only (Rule 12). */
public record DrugPriceUpdateRequest(
        @NotNull @DecimalMin(value = "0.0", inclusive = true)
        BigDecimal unitPrice
) {
}

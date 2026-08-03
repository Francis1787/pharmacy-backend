package com.app.pharmacy.domain.dtos.request;

import jakarta.validation.constraints.NotNull;

/** PATCH /drugs/{id}/controlled-status — Admin only (Rule 12). */
public record DrugControlledStatusUpdateRequest(
        @NotNull Boolean isControlledSubstance
) {
}

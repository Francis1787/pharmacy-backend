package com.app.pharmacy.domain.dtos.request;

import jakarta.validation.constraints.NotBlank;

/**
 * PATCH /drugs/{id} — Pharmacist or Technician. Deliberately excludes
 * unitPrice and isControlledSubstance; those are Admin-only fields
 * (Rule 12) with their own narrow request DTOs below.
 */
public record DrugCorrectionRequest(
        @NotBlank String name,
        String genericName
) {
}

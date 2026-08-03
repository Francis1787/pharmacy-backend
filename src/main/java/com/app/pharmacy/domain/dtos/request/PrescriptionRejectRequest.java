package com.app.pharmacy.domain.dtos.request;

import jakarta.validation.constraints.NotBlank;

/** PATCH /prescriptions/{id}/reject — Pharmacist only. Reason recorded in Prescription.notes. */
public record PrescriptionRejectRequest(
        @NotBlank String reason
) {
}

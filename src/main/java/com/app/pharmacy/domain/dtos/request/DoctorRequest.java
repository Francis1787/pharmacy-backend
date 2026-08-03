package com.app.pharmacy.domain.dtos.request;

import jakarta.validation.constraints.NotBlank;

/** Used for both create (Pharmacist only, Rule 14) and correction edits. */
public record DoctorRequest(
        @NotBlank String fullName,
        @NotBlank String licenseNumber,
        String contactInfo
) {
}

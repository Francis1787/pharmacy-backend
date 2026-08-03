package com.app.pharmacy.domain.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Used for both create (Pharmacist only, Rule 14) and correction edits. */
public record CustomerRequest(
        @NotBlank String fullName,

        @NotBlank
        @Pattern(regexp = "^[0-9+()\\-\\s]{7,20}$", message = "phoneNumber must be a valid phone number")
        String phoneNumber,

        String address
) {
}

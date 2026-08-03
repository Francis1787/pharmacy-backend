package com.app.pharmacy.domain.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Admin only (create and edit). */
public record SupplierRequest(
        @NotBlank String companyName,
        String contactPerson,

        @NotBlank
        @Pattern(regexp = "^[0-9+()\\-\\s]{7,20}$", message = "phoneNumber must be a valid phone number")
        String phoneNumber,

        @Email String email,
        String address
) {
}

package com.app.pharmacy.domain.dtos.request;

import com.app.pharmacy.domain.entity.enums.StaffRole;
import jakarta.validation.constraints.*;

/**
 * Admin-only (Rule 12) — role changes, contact updates, corrections.
 * Does not touch password_hash or must_reset_password; those are only
 * ever changed via the auth/change-password flow.
 */
public record StaffUpdateRequest(
        @NotBlank String fullName,
        @NotNull StaffRole role,
        String licenseNumber,

        @NotBlank
        @Pattern(regexp = "^[0-9+()\\-\\s]{7,20}$", message = "phoneNumber must be a valid phone number")
        String phoneNumber,

        @NotBlank @Email String email,
        @NotNull Boolean activeStatus
) {
    @AssertTrue(message = "licenseNumber is required for Pharmacist role and must be blank for other roles")
    public boolean isLicenseNumberValidForRole() {
        boolean hasLicense = licenseNumber != null && !licenseNumber.isBlank();
        return role == StaffRole.Pharmacist ? hasLicense : !hasLicense;
    }
}

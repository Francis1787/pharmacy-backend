package com.app.pharmacy.domain.dtos.request;

import com.app.pharmacy.domain.entity.enums.StaffRole;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

/**
 * Admin-only (Rule 13). If generatePassword is true, the service layer
 * generates a random temp password and returns it once in the response
 * (never retrievable again); otherwise tempPassword must be supplied.
 * must_reset_password is always forced TRUE on creation regardless of
 * which path is used.
 */
public record StaffCreateRequest(
        @NotBlank String fullName,
        @NotNull StaffRole role,

        /** Required if role == Pharmacist; must be blank otherwise (enforced below). */
        String licenseNumber,

        @NotBlank
        @Pattern(regexp = "^[0-9+()\\-\\s]{7,20}$", message = "phoneNumber must be a valid phone number")
        String phoneNumber,

        @NotBlank @Email String email,
        @NotNull LocalDate hireDate,

        boolean generatePassword,

        /** Required when generatePassword is false (enforced below). */
        String tempPassword
) {
    @AssertTrue(message = "licenseNumber is required for Pharmacist role and must be blank for other roles")
    public boolean isLicenseNumberValidForRole() {
        boolean hasLicense = licenseNumber != null && !licenseNumber.isBlank();
        return role == StaffRole.Pharmacist ? hasLicense : !hasLicense;
    }

    @AssertTrue(message = "tempPassword is required when generatePassword is false")
    public boolean isPasswordSetupValid() {
        return generatePassword || (tempPassword != null && !tempPassword.isBlank());
    }
}

package com.app.pharmacy.domain.dtos.response;

import com.app.pharmacy.domain.entity.enums.StaffRole;

import java.util.UUID;

/**
 * mustResetPassword mirrors Staff.must_reset_password — the frontend must
 * check this immediately after login and route to the password-change
 * screen before anything else is reachable (Rule 13).
 */
public record LoginResponse(
        String token,
        UUID staffId,
        String fullName,
        StaffRole role,
        boolean mustResetPassword
) {
}

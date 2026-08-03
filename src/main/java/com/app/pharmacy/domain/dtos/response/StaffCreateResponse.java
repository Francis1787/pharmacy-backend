package com.app.pharmacy.domain.dtos.response;

/**
 * Returned only from POST /staff, when generatePassword was true.
 * tempPassword is shown exactly once here — it is never retrievable
 * again since only the BCrypt hash is stored (Rule 13).
 */
public record StaffCreateResponse(
        StaffResponse staff,
        String tempPassword
) {
}

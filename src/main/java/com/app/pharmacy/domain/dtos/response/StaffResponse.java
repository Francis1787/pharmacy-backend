package com.app.pharmacy.domain.dtos.response;

import com.app.pharmacy.domain.entity.enums.StaffRole;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Deliberately excludes passwordHash — this is exactly the leak DTOs
 * exist to prevent (see Phase design notes: raw entities must never
 * reach the API response).
 */
public record StaffResponse(
        UUID id,
        String fullName,
        StaffRole role,
        String licenseNumber,
        String phoneNumber,
        String email,
        boolean mustResetPassword,
        LocalDate hireDate,
        boolean activeStatus
) {
}

package com.app.pharmacy.domain.dtos.response;

import java.util.UUID;

public record DoctorResponse(
        UUID id,
        String fullName,
        String licenseNumber,
        String contactInfo
) {
}

package com.app.pharmacy.domain.dtos.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String fullName,
        String phoneNumber,
        String address,
        LocalDateTime createdAt
) {
}

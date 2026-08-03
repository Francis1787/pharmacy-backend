package com.app.pharmacy.domain.dtos.response;

import java.util.UUID;

public record SupplierResponse(
        UUID id,
        String companyName,
        String contactPerson,
        String phoneNumber,
        String email,
        String address
) {
}

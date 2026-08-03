package com.app.pharmacy.domain.dtos.response;

import com.app.pharmacy.domain.dtos.common.RefSummary;

import java.util.UUID;

public record PrescriptionItemResponse(
        UUID id,
        RefSummary drug,
        String dosageInstructions,
        int quantityPrescribed
) {
}

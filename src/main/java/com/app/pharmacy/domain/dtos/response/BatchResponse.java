package com.app.pharmacy.domain.dtos.response;

import com.app.pharmacy.domain.dtos.common.RefSummary;

import java.time.LocalDate;
import java.util.UUID;

public record BatchResponse(
        UUID id,
        RefSummary drug,
        String batchNumber,
        int quantityInStock,
        LocalDate expiryDate,
        boolean isExpired,
        LocalDate dateReceived,
        RefSummary supplier,
        UUID purchaseOrderItemId,

        /** Null until a Pharmacist verifies the batch (Rule 11). */
        RefSummary verifiedByPharmacist,

        boolean isControlledSubstance
) {
}

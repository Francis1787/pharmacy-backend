package com.app.pharmacy.domain.dtos.response;

import com.app.pharmacy.domain.dtos.common.RefSummary;
import com.app.pharmacy.domain.entity.enums.ApprovalStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PrescriptionResponse(
        UUID id,
        RefSummary customer,
        RefSummary doctor,
        LocalDate dateIssued,
        LocalDateTime dateReceived,

        /** Null until a Pharmacist approves (Rule 2). */
        RefSummary approvingPharmacist,

        ApprovalStatus approvalStatus,
        String notes,
        List<PrescriptionItemResponse> items
) {
}

package com.app.pharmacy.domain.dtos.response;

import com.app.pharmacy.domain.dtos.common.RefSummary;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * No corresponding request DTO exists — every AuditLog row is written
 * server-side as a byproduct of another action (Rule 7), never created
 * directly via the API.
 */
public record AuditLogResponse(
        UUID id,
        RefSummary staff,
        String actionType,
        UUID referenceId,
        String referenceTable,
        LocalDateTime timestamp,
        String notes
) {
}

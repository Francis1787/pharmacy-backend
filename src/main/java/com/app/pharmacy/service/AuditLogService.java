package com.app.pharmacy.service;

import com.app.pharmacy.domain.dtos.response.AuditLogResponse;
import com.app.pharmacy.domain.entity.enums.ActionType;

import java.util.List;
import java.util.UUID;

/**
 * Centralizes AuditLog writes so every other service calls the same
 * logging path (Rule 7) rather than constructing AuditLog rows itself.
 * No corresponding create endpoint/DTO exists — entries only ever
 * originate from logAction() being called internally by another service.
 */
public interface AuditLogService {

    /**
     * Writes an audit entry. Called internally by PrescriptionService
     * (approve/reject), SaleService (dispense), BatchService (verify),
     * and PurchaseOrderService (create) — the only actions whose
     * action_type is permitted by the DB CHECK constraint.
     */
    void logAction(UUID staffId, ActionType actionType, UUID referenceId, String referenceTable, String notes);

    List<AuditLogResponse> getAllAuditLogs();

    List<AuditLogResponse> getAuditLogsByStaff(UUID staffId);

    List<AuditLogResponse> getAuditLogsByReference(String referenceTable, UUID referenceId);
}

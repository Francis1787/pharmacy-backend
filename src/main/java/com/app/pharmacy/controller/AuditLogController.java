package com.app.pharmacy.controller;

import com.app.pharmacy.domain.dtos.common.ApiResponse;
import com.app.pharmacy.domain.dtos.response.AuditLogResponse;
import com.app.pharmacy.service.AuditLogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Rule 7 — Admin-only visibility into the full audit trail for regulatory inspection. */
@Tag(name = "Audit Logs", description = "Compliance audit trail")
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AuditLogResponse>>> getAllAuditLogs(
            @RequestParam(required = false) UUID staffId,
            @RequestParam(required = false) String referenceTable,
            @RequestParam(required = false) UUID referenceId
    ) {
        List<AuditLogResponse> logs;
        if (staffId != null) {
            logs = auditLogService.getAuditLogsByStaff(staffId);
        } else if (referenceTable != null && referenceId != null) {
            logs = auditLogService.getAuditLogsByReference(referenceTable, referenceId);
        } else {
            logs = auditLogService.getAllAuditLogs();
        }
        return ResponseEntity.ok(ApiResponse.success(logs, "Audit logs retrieved successfully"));
    }
}

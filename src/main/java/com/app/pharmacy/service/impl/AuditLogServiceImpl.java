package com.app.pharmacy.service.impl;

import com.app.pharmacy.domain.dtos.common.RefSummary;
import com.app.pharmacy.domain.dtos.response.AuditLogResponse;
import com.app.pharmacy.domain.entity.AuditLog;
import com.app.pharmacy.domain.entity.Staff;
import com.app.pharmacy.domain.entity.enums.ActionType;
import com.app.pharmacy.exception.ResourceNotFoundException;
import com.app.pharmacy.repository.AuditLogRepository;
import com.app.pharmacy.repository.StaffRepository;
import com.app.pharmacy.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final StaffRepository staffRepository;

    @Override
    @Transactional
    public void logAction(UUID staffId, ActionType actionType, UUID referenceId, String referenceTable, String notes) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found: " + staffId));

        AuditLog entry = AuditLog.builder()
                .staff(staff)
                .actionType(actionType.dbValue())
                .referenceId(referenceId)
                .referenceTable(referenceTable)
                .notes(notes)
                .build();

        auditLogRepository.save(entry);
    }

    @Override
    public List<AuditLogResponse> getAllAuditLogs() {
        return auditLogRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    public List<AuditLogResponse> getAuditLogsByStaff(UUID staffId) {
        return auditLogRepository.findByStaffId(staffId).stream().map(this::toResponse).toList();
    }

    @Override
    public List<AuditLogResponse> getAuditLogsByReference(String referenceTable, UUID referenceId) {
        return auditLogRepository.findByReferenceTableAndReferenceId(referenceTable, referenceId)
                .stream().map(this::toResponse).toList();
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                new RefSummary(log.getStaff().getId(), log.getStaff().getFullName()),
                log.getActionType(),
                log.getReferenceId(),
                log.getReferenceTable(),
                log.getTimestamp(),
                log.getNotes()
        );
    }
}

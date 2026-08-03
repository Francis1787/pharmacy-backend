package com.app.pharmacy.repository;

import com.app.pharmacy.domain.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByStaffId(UUID staffId);

    List<AuditLog> findByActionType(String actionType);

    List<AuditLog> findByReferenceTableAndReferenceId(String referenceTable, UUID referenceId);

    List<AuditLog> findByTimestampBetween(LocalDateTime from, LocalDateTime to);
}

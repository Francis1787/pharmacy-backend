package com.app.pharmacy.service;

import com.app.pharmacy.domain.dtos.response.AuditLogResponse;
import com.app.pharmacy.domain.entity.AuditLog;
import com.app.pharmacy.domain.entity.Staff;
import com.app.pharmacy.domain.entity.enums.ActionType;
import com.app.pharmacy.exception.ResourceNotFoundException;
import com.app.pharmacy.repository.AuditLogRepository;
import com.app.pharmacy.repository.StaffRepository;
import com.app.pharmacy.service.impl.AuditLogServiceImpl;
import com.app.pharmacy.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The audit log is the system's accountability record (Rule 7). Every other
 * service writes through {@code logAction}, so a silent failure here means a
 * controlled-substance dispense happens with no trace of who did it.
 *
 * The critical detail under test is the enum-to-database translation: the
 * auditlog.action_type CHECK constraint permits only five specific strings
 * with spaces in them, and the enum constant names do not match. Writing
 * {@code actionType.name()} instead of {@code actionType.dbValue()} would
 * compile, pass a mocked-repository test that did not assert on it, and fail
 * only at runtime against the real database.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuditLogService — accountability trail")
class AuditLogServiceImplTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private StaffRepository staffRepository;

    @InjectMocks private AuditLogServiceImpl auditLogService;

    private Staff pharmacist;

    @BeforeEach
    void setUp() {
        pharmacist = TestFixtures.pharmacist();
        when(staffRepository.findById(pharmacist.getId())).thenReturn(Optional.of(pharmacist));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> {
            AuditLog entry = inv.getArgument(0);
            if (entry.getId() == null) {
                entry.setId(UUID.randomUUID());
            }
            return entry;
        });
    }

    private AuditLog capturedEntry() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("Writing entries")
    class Writing {

        @Test
        @DisplayName("records the acting staff member, target and note")
        void recordsFullEntry() {
            UUID saleId = UUID.randomUUID();

            auditLogService.logAction(pharmacist.getId(), ActionType.DRUG_DISPENSED, saleId, "Sale", "Dispensed sale");

            AuditLog entry = capturedEntry();
            assertThat(entry.getStaff().getId()).isEqualTo(pharmacist.getId());
            assertThat(entry.getReferenceId()).isEqualTo(saleId);
            assertThat(entry.getReferenceTable()).isEqualTo("Sale");
            assertThat(entry.getNotes()).isEqualTo("Dispensed sale");
        }

        @ParameterizedTest
        @EnumSource(ActionType.class)
        @DisplayName("stores the DB CHECK string, never the enum constant name")
        void storesDbValueNotConstantName(ActionType type) {
            auditLogService.logAction(pharmacist.getId(), type, UUID.randomUUID(), "Sale", "note");

            AuditLog entry = capturedEntry();
            assertThat(entry.getActionType())
                    .as("auditlog.action_type only permits the five spaced values")
                    .isEqualTo(type.dbValue());
            assertThat(entry.getActionType()).isNotEqualTo(type.name());
        }

        @Test
        @DisplayName("refuses to attribute an action to an unknown staff id")
        void rejectsUnknownStaff() {
            UUID ghost = UUID.randomUUID();
            when(staffRepository.findById(ghost)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> auditLogService.logAction(
                    ghost, ActionType.DRUG_DISPENSED, UUID.randomUUID(), "Sale", "note"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Staff not found");

            verify(auditLogRepository, never()).save(any());
        }

        @Test
        @DisplayName("accepts a null note — not every action carries commentary")
        void allowsNullNote() {
            auditLogService.logAction(pharmacist.getId(), ActionType.STOCK_UPDATED, UUID.randomUUID(), "Batch", null);

            assertThat(capturedEntry().getNotes()).isNull();
        }
    }

    @Nested
    @DisplayName("Reading entries")
    class Reading {

        private AuditLog entry() {
            return AuditLog.builder()
                    .id(UUID.randomUUID())
                    .staff(pharmacist)
                    .actionType(ActionType.PRESCRIPTION_APPROVED.dbValue())
                    .referenceId(UUID.randomUUID())
                    .referenceTable("Prescription")
                    .timestamp(LocalDateTime.now())
                    .notes("Reviewed and approved for dispensing")
                    .build();
        }

        @Test
        @DisplayName("exposes the acting staff member by name, not just id")
        void resolvesStaffName() {
            when(auditLogRepository.findAll()).thenReturn(List.of(entry()));

            List<AuditLogResponse> logs = auditLogService.getAllAuditLogs();

            assertThat(logs).hasSize(1);
            assertThat(logs.getFirst().staff().label()).isEqualTo(pharmacist.getFullName());
            assertThat(logs.getFirst().staff().id()).isEqualTo(pharmacist.getId());
        }

        @Test
        @DisplayName("filters by staff member")
        void filtersByStaff() {
            when(auditLogRepository.findByStaffId(pharmacist.getId())).thenReturn(List.of(entry()));

            assertThat(auditLogService.getAuditLogsByStaff(pharmacist.getId())).hasSize(1);
            verify(auditLogRepository).findByStaffId(pharmacist.getId());
            verify(auditLogRepository, never()).findAll();
        }

        @Test
        @DisplayName("filters by the record an action was performed against")
        void filtersByReference() {
            UUID referenceId = UUID.randomUUID();
            when(auditLogRepository.findByReferenceTableAndReferenceId("Sale", referenceId))
                    .thenReturn(List.of(entry()));

            assertThat(auditLogService.getAuditLogsByReference("Sale", referenceId)).hasSize(1);
            verify(auditLogRepository).findByReferenceTableAndReferenceId("Sale", referenceId);
        }

        @Test
        @DisplayName("returns an empty list rather than null when nothing matches")
        void emptyWhenNoMatches() {
            when(auditLogRepository.findByStaffId(any())).thenReturn(List.of());

            assertThat(auditLogService.getAuditLogsByStaff(UUID.randomUUID())).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("passes the raw action_type string through unchanged for display")
        void exposesRawActionType() {
            when(auditLogRepository.findAll()).thenReturn(List.of(entry()));

            assertThat(auditLogService.getAllAuditLogs().getFirst().actionType())
                    .isEqualTo("Prescription Approved");
        }
    }
}

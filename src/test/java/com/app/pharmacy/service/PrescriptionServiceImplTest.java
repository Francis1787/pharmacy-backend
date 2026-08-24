package com.app.pharmacy.service;

import com.app.pharmacy.domain.dtos.request.PrescriptionCreateRequest;
import com.app.pharmacy.domain.dtos.request.PrescriptionRejectRequest;
import com.app.pharmacy.domain.dtos.response.PrescriptionResponse;
import com.app.pharmacy.domain.entity.*;
import com.app.pharmacy.domain.entity.enums.ActionType;
import com.app.pharmacy.domain.entity.enums.ApprovalStatus;
import com.app.pharmacy.exception.BusinessRuleViolationException;
import com.app.pharmacy.exception.ResourceNotFoundException;
import com.app.pharmacy.repository.*;
import com.app.pharmacy.service.impl.PrescriptionServiceImpl;
import com.app.pharmacy.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Prescription intake and the approve/reject state machine (Rule 2).
 *
 * The state machine matters as much as the role check: a prescription that
 * can be re-approved after rejection, or approved twice, is a direct route
 * to a double dispense downstream.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PrescriptionService — intake and verification")
class PrescriptionServiceImplTest {

    @Mock private PrescriptionRepository prescriptionRepository;
    @Mock private PrescriptionItemRepository prescriptionItemRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private DoctorRepository doctorRepository;
    @Mock private DrugRepository drugRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private PrescriptionServiceImpl prescriptionService;

    private Staff pharmacist;

    @BeforeEach
    void setUp() {
        pharmacist = TestFixtures.pharmacist();
        when(staffRepository.findById(pharmacist.getId())).thenReturn(Optional.of(pharmacist));
        when(prescriptionRepository.save(any(Prescription.class))).thenAnswer(inv -> {
            Prescription p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
            }
            return p;
        });
        when(prescriptionItemRepository.save(any(PrescriptionItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(prescriptionItemRepository.findByPrescriptionId(any())).thenReturn(List.of());
    }

    @Nested
    @DisplayName("Intake")
    class Intake {

        private PrescriptionCreateRequest request(Customer customer, Doctor doctor, Drug drug) {
            return new PrescriptionCreateRequest(
                    customer.getId(), doctor.getId(), LocalDate.now().minusDays(1), "Take after meals",
                    List.of(new PrescriptionCreateRequest.Item(drug.getId(), "1 tablet twice daily", 20)));
        }

        @Test
        @DisplayName("always starts a new prescription as Pending, whoever logs it")
        void startsPending() {
            Customer customer = TestFixtures.customer();
            Doctor doctor = TestFixtures.doctor();
            Drug drug = TestFixtures.drug();
            when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
            when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
            when(drugRepository.findById(drug.getId())).thenReturn(Optional.of(drug));

            PrescriptionResponse response = prescriptionService.createPrescription(request(customer, doctor, drug));

            assertThat(response.approvalStatus()).isEqualTo(ApprovalStatus.Pending);
            assertThat(response.approvingPharmacist()).isNull();
        }

        @Test
        @DisplayName("persists one PrescriptionItem per requested line")
        void persistsEachLineItem() {
            Customer customer = TestFixtures.customer();
            Doctor doctor = TestFixtures.doctor();
            Drug a = TestFixtures.drug("Amoxicillin", new java.math.BigDecimal("4.00"), false);
            Drug b = TestFixtures.drug("Ibuprofen", new java.math.BigDecimal("3.00"), false);
            when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
            when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
            when(drugRepository.findById(a.getId())).thenReturn(Optional.of(a));
            when(drugRepository.findById(b.getId())).thenReturn(Optional.of(b));

            prescriptionService.createPrescription(new PrescriptionCreateRequest(
                    customer.getId(), doctor.getId(), LocalDate.now(), null,
                    List.of(new PrescriptionCreateRequest.Item(a.getId(), "1 cap 3x daily", 21),
                            new PrescriptionCreateRequest.Item(b.getId(), "1 tab as needed", 10))));

            verify(prescriptionItemRepository, times(2)).save(any(PrescriptionItem.class));
        }

        @Test
        @DisplayName("404s on an unknown customer")
        void unknownCustomer() {
            UUID unknown = UUID.randomUUID();
            when(customerRepository.findById(unknown)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> prescriptionService.createPrescription(new PrescriptionCreateRequest(
                    unknown, UUID.randomUUID(), LocalDate.now(), null,
                    List.of(new PrescriptionCreateRequest.Item(UUID.randomUUID(), "dose", 1)))))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Customer not found");
        }

        @Test
        @DisplayName("404s on an unknown drug in a line item")
        void unknownDrugInItem() {
            Customer customer = TestFixtures.customer();
            Doctor doctor = TestFixtures.doctor();
            UUID unknownDrug = UUID.randomUUID();
            when(customerRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
            when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
            when(drugRepository.findById(unknownDrug)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> prescriptionService.createPrescription(new PrescriptionCreateRequest(
                    customer.getId(), doctor.getId(), LocalDate.now(), null,
                    List.of(new PrescriptionCreateRequest.Item(unknownDrug, "dose", 1)))))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Drug not found");
        }
    }

    @Nested
    @DisplayName("Rule 2 — only a Pharmacist may approve or reject")
    class RoleGate {

        @Test
        @DisplayName("rejects approval by a Technician")
        void technicianCannotApprove() {
            Staff technician = TestFixtures.technician();
            Prescription pending = TestFixtures.prescription(ApprovalStatus.Pending);
            when(staffRepository.findById(technician.getId())).thenReturn(Optional.of(technician));
            when(prescriptionRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

            assertThatThrownBy(() -> prescriptionService.approvePrescription(pending.getId(), technician.getId()))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("Rule 2");

            assertThat(pending.getApprovalStatus()).isEqualTo(ApprovalStatus.Pending);
        }

        @Test
        @DisplayName("rejects approval by an Admin")
        void adminCannotApprove() {
            Staff admin = TestFixtures.admin();
            Prescription pending = TestFixtures.prescription(ApprovalStatus.Pending);
            when(staffRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
            when(prescriptionRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

            assertThatThrownBy(() -> prescriptionService.approvePrescription(pending.getId(), admin.getId()))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("rejects rejection by a Technician too")
        void technicianCannotReject() {
            Staff technician = TestFixtures.technician();
            Prescription pending = TestFixtures.prescription(ApprovalStatus.Pending);
            when(staffRepository.findById(technician.getId())).thenReturn(Optional.of(technician));
            when(prescriptionRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

            assertThatThrownBy(() -> prescriptionService.rejectPrescription(
                    pending.getId(), technician.getId(), new PrescriptionRejectRequest("Illegible")))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }

    @Nested
    @DisplayName("Approval state machine")
    class StateMachine {

        @Test
        @DisplayName("moves Pending to Approved and records the approving pharmacist")
        void approvesPending() {
            Prescription pending = TestFixtures.prescription(ApprovalStatus.Pending);
            when(prescriptionRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

            PrescriptionResponse response = prescriptionService.approvePrescription(pending.getId(), pharmacist.getId());

            assertThat(response.approvalStatus()).isEqualTo(ApprovalStatus.Approved);
            assertThat(response.approvingPharmacist().id()).isEqualTo(pharmacist.getId());
        }

        @Test
        @DisplayName("refuses to approve an already-Approved prescription")
        void cannotApproveTwice() {
            Prescription approved = TestFixtures.prescription(ApprovalStatus.Approved);
            when(prescriptionRepository.findById(approved.getId())).thenReturn(Optional.of(approved));

            assertThatThrownBy(() -> prescriptionService.approvePrescription(approved.getId(), pharmacist.getId()))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("cannot be approved again");
        }

        @Test
        @DisplayName("refuses to approve a Rejected prescription")
        void cannotApproveRejected() {
            Prescription rejected = TestFixtures.prescription(ApprovalStatus.Rejected);
            when(prescriptionRepository.findById(rejected.getId())).thenReturn(Optional.of(rejected));

            assertThatThrownBy(() -> prescriptionService.approvePrescription(rejected.getId(), pharmacist.getId()))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        @DisplayName("refuses to reject an already-Approved prescription")
        void cannotRejectApproved() {
            Prescription approved = TestFixtures.prescription(ApprovalStatus.Approved);
            when(prescriptionRepository.findById(approved.getId())).thenReturn(Optional.of(approved));

            assertThatThrownBy(() -> prescriptionService.rejectPrescription(
                    approved.getId(), pharmacist.getId(), new PrescriptionRejectRequest("Changed my mind")))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("cannot be rejected");
        }

        @Test
        @DisplayName("appends the rejection reason to existing notes rather than overwriting them")
        void appendsRejectionReason() {
            Prescription pending = TestFixtures.prescription(ApprovalStatus.Pending);
            pending.setNotes("Patient allergic to penicillin");
            when(prescriptionRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

            PrescriptionResponse response = prescriptionService.rejectPrescription(
                    pending.getId(), pharmacist.getId(), new PrescriptionRejectRequest("Dosage exceeds safe limit"));

            assertThat(response.notes())
                    .contains("Patient allergic to penicillin")
                    .contains("Rejected: Dosage exceeds safe limit");
        }

        @Test
        @DisplayName("sets the note cleanly when the prescription had none")
        void setsRejectionReasonWhenNoNotes() {
            Prescription pending = TestFixtures.prescription(ApprovalStatus.Pending);
            pending.setNotes(null);
            when(prescriptionRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

            PrescriptionResponse response = prescriptionService.rejectPrescription(
                    pending.getId(), pharmacist.getId(), new PrescriptionRejectRequest("Expired script"));

            assertThat(response.notes()).isEqualTo("Rejected: Expired script");
        }

        @Test
        @DisplayName("404s on an unknown prescription")
        void unknownPrescription() {
            UUID unknown = UUID.randomUUID();
            when(prescriptionRepository.findById(unknown)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> prescriptionService.approvePrescription(unknown, pharmacist.getId()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Rule 7 — verification decisions are audited")
    class Auditing {

        @Test
        @DisplayName("logs PRESCRIPTION_APPROVED on approval")
        void logsApproval() {
            Prescription pending = TestFixtures.prescription(ApprovalStatus.Pending);
            when(prescriptionRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

            prescriptionService.approvePrescription(pending.getId(), pharmacist.getId());

            verify(auditLogService).logAction(eq(pharmacist.getId()), eq(ActionType.PRESCRIPTION_APPROVED),
                    eq(pending.getId()), eq("Prescription"), anyString());
        }

        @Test
        @DisplayName("logs PRESCRIPTION_REJECTED carrying the stated reason")
        void logsRejectionWithReason() {
            Prescription pending = TestFixtures.prescription(ApprovalStatus.Pending);
            when(prescriptionRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

            ArgumentCaptor<String> notes = ArgumentCaptor.forClass(String.class);
            prescriptionService.rejectPrescription(
                    pending.getId(), pharmacist.getId(), new PrescriptionRejectRequest("Suspected forgery"));
            verify(auditLogService).logAction(any(), eq(ActionType.PRESCRIPTION_REJECTED), any(), anyString(),
                    notes.capture());

            assertThat(notes.getValue()).isEqualTo("Suspected forgery");
        }

        @Test
        @DisplayName("writes no audit entry when the role check refuses the action")
        void noAuditOnRefusal() {
            Staff technician = TestFixtures.technician();
            Prescription pending = TestFixtures.prescription(ApprovalStatus.Pending);
            when(staffRepository.findById(technician.getId())).thenReturn(Optional.of(technician));
            when(prescriptionRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

            assertThatThrownBy(() -> prescriptionService.approvePrescription(pending.getId(), technician.getId()))
                    .isInstanceOf(BusinessRuleViolationException.class);

            verifyNoInteractions(auditLogService);
        }
    }
}

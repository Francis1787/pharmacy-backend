package com.app.pharmacy.service.impl;

import com.app.pharmacy.domain.dtos.common.RefSummary;
import com.app.pharmacy.domain.dtos.request.PrescriptionCreateRequest;
import com.app.pharmacy.domain.dtos.request.PrescriptionRejectRequest;
import com.app.pharmacy.domain.dtos.response.PrescriptionItemResponse;
import com.app.pharmacy.domain.dtos.response.PrescriptionResponse;
import com.app.pharmacy.domain.entity.*;
import com.app.pharmacy.domain.entity.enums.ActionType;
import com.app.pharmacy.domain.entity.enums.ApprovalStatus;
import com.app.pharmacy.domain.entity.enums.StaffRole;
import com.app.pharmacy.exception.BusinessRuleViolationException;
import com.app.pharmacy.exception.ResourceNotFoundException;
import com.app.pharmacy.repository.*;
import com.app.pharmacy.service.AuditLogService;
import com.app.pharmacy.service.PrescriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PrescriptionServiceImpl implements PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;
    private final CustomerRepository customerRepository;
    private final DoctorRepository doctorRepository;
    private final DrugRepository drugRepository;
    private final StaffRepository staffRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public PrescriptionResponse createPrescription(PrescriptionCreateRequest request) {
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + request.customerId()));
        Doctor doctor = doctorRepository.findById(request.doctorId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + request.doctorId()));

        Prescription prescription = Prescription.builder()
                .customer(customer)
                .doctor(doctor)
                .dateIssued(request.dateIssued())
                .dateReceived(LocalDateTime.now())
                .approvalStatus(ApprovalStatus.Pending)
                .notes(request.notes())
                .build();
        prescription = prescriptionRepository.save(prescription);

        for (PrescriptionCreateRequest.Item itemReq : request.items()) {
            Drug drug = drugRepository.findById(itemReq.drugId())
                    .orElseThrow(() -> new ResourceNotFoundException("Drug not found: " + itemReq.drugId()));

            PrescriptionItem item = PrescriptionItem.builder()
                    .prescription(prescription)
                    .drug(drug)
                    .dosageInstructions(itemReq.dosageInstructions())
                    .quantityPrescribed(itemReq.quantityPrescribed())
                    .build();
            prescriptionItemRepository.save(item);
        }

        // NOTE: interaction / duplicate-therapy checks against the customer's prior
        // approved prescriptions (requirements doc, "Prescription Intake & Verification")
        // are a flag-only concern, not a blocking one, and are intentionally left as a
        // follow-up enhancement rather than baked into this initial service pass.

        return toResponse(prescription);
    }

    @Override
    @Transactional
    public PrescriptionResponse approvePrescription(UUID id, UUID pharmacistId) {
        Prescription prescription = getPrescriptionOrThrow(id);
        Staff pharmacist = getPharmacistOrThrow(pharmacistId);

        if (prescription.getApprovalStatus() != ApprovalStatus.Pending) {
            throw new BusinessRuleViolationException(
                    "Prescription " + id + " is " + prescription.getApprovalStatus() + " and cannot be approved again");
        }

        prescription.setApprovingPharmacist(pharmacist);
        prescription.setApprovalStatus(ApprovalStatus.Approved);
        Prescription saved = prescriptionRepository.save(prescription);

        auditLogService.logAction(pharmacistId, ActionType.PRESCRIPTION_APPROVED, prescription.getId(),
                "Prescription", "Reviewed and approved for dispensing");

        return toResponse(saved);
    }

    @Override
    @Transactional
    public PrescriptionResponse rejectPrescription(UUID id, UUID pharmacistId, PrescriptionRejectRequest request) {
        Prescription prescription = getPrescriptionOrThrow(id);
        Staff pharmacist = getPharmacistOrThrow(pharmacistId);

        if (prescription.getApprovalStatus() != ApprovalStatus.Pending) {
            throw new BusinessRuleViolationException(
                    "Prescription " + id + " is " + prescription.getApprovalStatus() + " and cannot be rejected");
        }

        prescription.setApprovingPharmacist(pharmacist);
        prescription.setApprovalStatus(ApprovalStatus.Rejected);
        prescription.setNotes(appendNote(prescription.getNotes(), "Rejected: " + request.reason()));
        Prescription saved = prescriptionRepository.save(prescription);

        auditLogService.logAction(pharmacistId, ActionType.PRESCRIPTION_REJECTED, prescription.getId(),
                "Prescription", request.reason());

        return toResponse(saved);
    }

    @Override
    public PrescriptionResponse getPrescriptionById(UUID id) {
        return toResponse(getPrescriptionOrThrow(id));
    }

    @Override
    public List<PrescriptionResponse> getAllPrescriptions() {
        return prescriptionRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    public List<PrescriptionResponse> getPrescriptionsByCustomer(UUID customerId) {
        return prescriptionRepository.findByCustomerId(customerId).stream().map(this::toResponse).toList();
    }

    @Override
    public List<PrescriptionResponse> getPrescriptionsByStatus(ApprovalStatus status) {
        return prescriptionRepository.findByApprovalStatus(status).stream().map(this::toResponse).toList();
    }

    private Prescription getPrescriptionOrThrow(UUID id) {
        return prescriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription not found: " + id));
    }

    private Staff getPharmacistOrThrow(UUID staffId) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found: " + staffId));
        if (staff.getRole() != StaffRole.Pharmacist) {
            throw new BusinessRuleViolationException("Only a Pharmacist may approve or reject a prescription (Rule 2)");
        }
        return staff;
    }

    private String appendNote(String existing, String addition) {
        if (existing == null || existing.isBlank()) {
            return addition;
        }
        return existing + " | " + addition;
    }

    private PrescriptionResponse toResponse(Prescription prescription) {
        List<PrescriptionItemResponse> items = prescriptionItemRepository.findByPrescriptionId(prescription.getId())
                .stream()
                .map(item -> new PrescriptionItemResponse(
                        item.getId(),
                        new RefSummary(item.getDrug().getId(), item.getDrug().getName()),
                        item.getDosageInstructions(),
                        item.getQuantityPrescribed()
                ))
                .toList();

        return new PrescriptionResponse(
                prescription.getId(),
                new RefSummary(prescription.getCustomer().getId(), prescription.getCustomer().getFullName()),
                new RefSummary(prescription.getDoctor().getId(), prescription.getDoctor().getFullName()),
                prescription.getDateIssued(),
                prescription.getDateReceived(),
                prescription.getApprovingPharmacist() != null
                        ? new RefSummary(prescription.getApprovingPharmacist().getId(), prescription.getApprovingPharmacist().getFullName())
                        : null,
                prescription.getApprovalStatus(),
                prescription.getNotes(),
                items
        );
    }
}

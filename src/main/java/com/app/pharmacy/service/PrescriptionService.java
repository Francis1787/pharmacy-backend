package com.app.pharmacy.service;

import com.app.pharmacy.domain.dtos.request.PrescriptionCreateRequest;
import com.app.pharmacy.domain.dtos.request.PrescriptionRejectRequest;
import com.app.pharmacy.domain.dtos.response.PrescriptionResponse;
import com.app.pharmacy.domain.entity.enums.ApprovalStatus;

import java.util.List;
import java.util.UUID;

public interface PrescriptionService {

    /** Pharmacist or Technician — intake logging. Always starts approvalStatus = Pending. */
    PrescriptionResponse createPrescription(PrescriptionCreateRequest request);

    /** Pharmacist only (Rule 2). pharmacistId is the authenticated caller, not client-supplied. */
    PrescriptionResponse approvePrescription(UUID id, UUID pharmacistId);

    /** Pharmacist only. Reason is appended to Prescription.notes. */
    PrescriptionResponse rejectPrescription(UUID id, UUID pharmacistId, PrescriptionRejectRequest request);

    PrescriptionResponse getPrescriptionById(UUID id);

    List<PrescriptionResponse> getAllPrescriptions();

    List<PrescriptionResponse> getPrescriptionsByCustomer(UUID customerId);

    List<PrescriptionResponse> getPrescriptionsByStatus(ApprovalStatus status);
}

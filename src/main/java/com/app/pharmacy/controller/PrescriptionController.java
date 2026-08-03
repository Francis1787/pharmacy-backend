package com.app.pharmacy.controller;

import com.app.pharmacy.domain.dtos.common.ApiResponse;
import com.app.pharmacy.domain.dtos.request.PrescriptionCreateRequest;
import com.app.pharmacy.domain.dtos.request.PrescriptionRejectRequest;
import com.app.pharmacy.domain.dtos.response.PrescriptionResponse;
import com.app.pharmacy.domain.entity.enums.ApprovalStatus;
import com.app.pharmacy.security.CustomUserDetails;
import com.app.pharmacy.service.PrescriptionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Prescriptions", description = "Prescription intake, approval, and rejection")
@RestController
@RequestMapping("/api/v1/prescriptions")
@RequiredArgsConstructor
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    @PreAuthorize("hasAnyRole('PHARMACIST', 'TECHNICIAN')")
    @PostMapping
    public ResponseEntity<ApiResponse<PrescriptionResponse>> createPrescription(
            @Valid @RequestBody PrescriptionCreateRequest request) {
        PrescriptionResponse response = prescriptionService.createPrescription(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Prescription logged successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PrescriptionResponse>>> getAllPrescriptions(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) ApprovalStatus status
    ) {
        List<PrescriptionResponse> prescriptions;
        if (customerId != null) {
            prescriptions = prescriptionService.getPrescriptionsByCustomer(customerId);
        } else if (status != null) {
            prescriptions = prescriptionService.getPrescriptionsByStatus(status);
        } else {
            prescriptions = prescriptionService.getAllPrescriptions();
        }
        return ResponseEntity.ok(ApiResponse.success(prescriptions, "Prescriptions retrieved successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> getPrescriptionById(@PathVariable UUID id) {
        PrescriptionResponse response = prescriptionService.getPrescriptionById(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Prescription retrieved successfully"));
    }

    /** Rule 2 — only a Pharmacist may approve. */
    @PreAuthorize("hasRole('PHARMACIST')")
    @PatchMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> approvePrescription(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        PrescriptionResponse response = prescriptionService.approvePrescription(id, principal.getStaffId());
        return ResponseEntity.ok(ApiResponse.success(response, "Prescription approved successfully"));
    }

    /** Only a Pharmacist may reject. */
    @PreAuthorize("hasRole('PHARMACIST')")
    @PatchMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<PrescriptionResponse>> rejectPrescription(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody PrescriptionRejectRequest request
    ) {
        PrescriptionResponse response = prescriptionService.rejectPrescription(id, principal.getStaffId(), request);
        return ResponseEntity.ok(ApiResponse.success(response, "Prescription rejected"));
    }
}

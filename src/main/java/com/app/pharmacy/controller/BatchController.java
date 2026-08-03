package com.app.pharmacy.controller;

import com.app.pharmacy.domain.dtos.common.ApiResponse;
import com.app.pharmacy.domain.dtos.request.BatchCreateRequest;
import com.app.pharmacy.domain.dtos.response.BatchResponse;
import com.app.pharmacy.security.CustomUserDetails;
import com.app.pharmacy.service.BatchService;
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

@Tag(name = "Batches", description = "Stock batch receiving and verification")
@RestController
@RequestMapping("/api/v1/batches")
@RequiredArgsConstructor
public class BatchController {

    private final BatchService batchService;

    @PreAuthorize("hasAnyRole('PHARMACIST', 'TECHNICIAN')")
    @PostMapping
    public ResponseEntity<ApiResponse<BatchResponse>> createBatch(@Valid @RequestBody BatchCreateRequest request) {
        BatchResponse response = batchService.createBatch(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Batch logged successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BatchResponse>>> getAllBatches(
            @RequestParam(required = false) UUID drugId,
            @RequestParam(required = false, name = "expiring-within-days") Integer expiringWithinDays
    ) {
        List<BatchResponse> batches;
        if (drugId != null) {
            batches = batchService.getBatchesByDrug(drugId);
        } else if (expiringWithinDays != null) {
            batches = batchService.getBatchesExpiringWithinDays(expiringWithinDays);
        } else {
            batches = batchService.getAllBatches();
        }
        return ResponseEntity.ok(ApiResponse.success(batches, "Batches retrieved successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BatchResponse>> getBatchById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(batchService.getBatchById(id), "Batch retrieved successfully"));
    }

    /** Rule 11 — only a Pharmacist may verify a delivered batch, mandatory for controlled substances. */
    @PreAuthorize("hasRole('PHARMACIST')")
    @PatchMapping("/{id}/verify")
    public ResponseEntity<ApiResponse<BatchResponse>> verifyBatch(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        BatchResponse response = batchService.verifyBatch(id, principal.getStaffId());
        return ResponseEntity.ok(ApiResponse.success(response, "Batch verified successfully"));
    }
}

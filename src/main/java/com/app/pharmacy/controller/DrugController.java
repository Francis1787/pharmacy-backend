package com.app.pharmacy.controller;

import com.app.pharmacy.domain.dtos.common.ApiResponse;
import com.app.pharmacy.domain.dtos.request.DrugControlledStatusUpdateRequest;
import com.app.pharmacy.domain.dtos.request.DrugCorrectionRequest;
import com.app.pharmacy.domain.dtos.request.DrugCreateRequest;
import com.app.pharmacy.domain.dtos.request.DrugPriceUpdateRequest;
import com.app.pharmacy.domain.dtos.response.DrugResponse;
import com.app.pharmacy.service.DrugService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Drugs", description = "Drug catalog management")
@RestController
@RequestMapping("/api/v1/drugs")
@RequiredArgsConstructor
public class DrugController {

    private final DrugService drugService;

    @PreAuthorize("hasAnyRole('PHARMACIST', 'TECHNICIAN')")
    @PostMapping
    public ResponseEntity<ApiResponse<DrugResponse>> createDrug(@Valid @RequestBody DrugCreateRequest request) {
        DrugResponse response = drugService.createDrug(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Drug created successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DrugResponse>>> getAllDrugs(@RequestParam(required = false) String name) {
        List<DrugResponse> drugs = (name != null && !name.isBlank())
                ? drugService.searchDrugsByName(name)
                : drugService.getAllDrugs();
        return ResponseEntity.ok(ApiResponse.success(drugs, "Drugs retrieved successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DrugResponse>> getDrugById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(drugService.getDrugById(id), "Drug retrieved successfully"));
    }

    /** Rule 8 — drugs whose stock has fallen to/below their reorder threshold. */
    @GetMapping("/below-threshold")
    public ResponseEntity<ApiResponse<List<DrugResponse>>> getDrugsBelowThreshold() {
        List<DrugResponse> drugs = drugService.getDrugsBelowReorderThreshold();
        return ResponseEntity.ok(ApiResponse.success(drugs, "Drugs below reorder threshold retrieved successfully"));
    }

    /** name/generic_name only — the narrow correction scope, open to Pharmacist and Technician. */
    @PreAuthorize("hasAnyRole('PHARMACIST', 'TECHNICIAN')")
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<DrugResponse>> correctDrug(
            @PathVariable UUID id, @Valid @RequestBody DrugCorrectionRequest request) {
        DrugResponse response = drugService.correctDrug(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Drug details corrected successfully"));
    }

    /** Rule 12 — unit_price is Admin-only. */
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/price")
    public ResponseEntity<ApiResponse<DrugResponse>> updatePrice(
            @PathVariable UUID id, @Valid @RequestBody DrugPriceUpdateRequest request) {
        DrugResponse response = drugService.updatePrice(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Drug price updated successfully"));
    }

    /** Rule 12 — is_controlled_substance is Admin-only. */
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/controlled-status")
    public ResponseEntity<ApiResponse<DrugResponse>> updateControlledStatus(
            @PathVariable UUID id, @Valid @RequestBody DrugControlledStatusUpdateRequest request) {
        DrugResponse response = drugService.updateControlledStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Drug controlled-substance status updated successfully"));
    }
}

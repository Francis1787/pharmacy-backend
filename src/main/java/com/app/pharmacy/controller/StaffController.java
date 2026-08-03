package com.app.pharmacy.controller;

import com.app.pharmacy.domain.dtos.common.ApiResponse;
import com.app.pharmacy.domain.dtos.request.StaffCreateRequest;
import com.app.pharmacy.domain.dtos.request.StaffUpdateRequest;
import com.app.pharmacy.domain.dtos.response.StaffCreateResponse;
import com.app.pharmacy.domain.dtos.response.StaffResponse;
import com.app.pharmacy.service.StaffService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Every endpoint here is Admin-only (Rule 13) — only Admin manages staff accounts. */
@Tag(name = "Staff", description = "Admin-only staff account management")
@RestController
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class StaffController {

    private final StaffService staffService;

    @PostMapping
    public ResponseEntity<ApiResponse<StaffCreateResponse>> createStaff(@Valid @RequestBody StaffCreateRequest request) {
        StaffCreateResponse response = staffService.createStaff(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Staff account created successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<StaffResponse>>> getAllStaff() {
        return ResponseEntity.ok(ApiResponse.success(staffService.getAllStaff(), "Staff retrieved successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StaffResponse>> getStaffById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(staffService.getStaffById(id), "Staff retrieved successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<StaffResponse>> updateStaff(
            @PathVariable UUID id, @Valid @RequestBody StaffUpdateRequest request) {
        StaffResponse response = staffService.updateStaff(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Staff account updated successfully"));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<StaffResponse>> deactivateStaff(@PathVariable UUID id) {
        StaffResponse response = staffService.deactivateStaff(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Staff account deactivated successfully"));
    }
}

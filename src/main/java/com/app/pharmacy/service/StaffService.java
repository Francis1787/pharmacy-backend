package com.app.pharmacy.service;

import com.app.pharmacy.domain.dtos.request.StaffCreateRequest;
import com.app.pharmacy.domain.dtos.request.StaffUpdateRequest;
import com.app.pharmacy.domain.dtos.response.StaffCreateResponse;
import com.app.pharmacy.domain.dtos.response.StaffResponse;

import java.util.List;
import java.util.UUID;

/** All operations here are Admin-only (Rule 13) — enforced at the controller layer. */
public interface StaffService {

    /**
     * Creates a staff account. must_reset_password is always forced TRUE.
     * If request.generatePassword() is true, a random temp password is
     * generated and returned once in the response (never retrievable again).
     */
    StaffCreateResponse createStaff(StaffCreateRequest request);

    StaffResponse updateStaff(UUID id, StaffUpdateRequest request);

    StaffResponse deactivateStaff(UUID id);

    StaffResponse getStaffById(UUID id);

    List<StaffResponse> getAllStaff();
}

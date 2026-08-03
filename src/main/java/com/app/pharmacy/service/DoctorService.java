package com.app.pharmacy.service;

import com.app.pharmacy.domain.dtos.request.DoctorRequest;
import com.app.pharmacy.domain.dtos.response.DoctorResponse;

import java.util.List;
import java.util.UUID;

/** Create and edit are Pharmacist-only (Rule 14) — enforced at the controller layer. */
public interface DoctorService {

    DoctorResponse createDoctor(DoctorRequest request);

    DoctorResponse updateDoctor(UUID id, DoctorRequest request);

    DoctorResponse getDoctorById(UUID id);

    List<DoctorResponse> getAllDoctors();

    List<DoctorResponse> searchDoctorsByName(String name);
}

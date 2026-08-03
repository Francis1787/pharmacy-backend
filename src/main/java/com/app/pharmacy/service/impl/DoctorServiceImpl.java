package com.app.pharmacy.service.impl;

import com.app.pharmacy.domain.dtos.request.DoctorRequest;
import com.app.pharmacy.domain.dtos.response.DoctorResponse;
import com.app.pharmacy.domain.entity.Doctor;
import com.app.pharmacy.exception.DuplicateResourceException;
import com.app.pharmacy.exception.ResourceNotFoundException;
import com.app.pharmacy.repository.DoctorRepository;
import com.app.pharmacy.service.DoctorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DoctorServiceImpl implements DoctorService {

    private final DoctorRepository doctorRepository;

    @Override
    @Transactional
    public DoctorResponse createDoctor(DoctorRequest request) {
        doctorRepository.findByLicenseNumber(request.licenseNumber()).ifPresent(existing -> {
            throw new DuplicateResourceException("A doctor with license number " + request.licenseNumber() + " already exists");
        });

        Doctor doctor = Doctor.builder()
                .fullName(request.fullName())
                .licenseNumber(request.licenseNumber())
                .contactInfo(request.contactInfo())
                .build();

        return toResponse(doctorRepository.save(doctor));
    }

    @Override
    @Transactional
    public DoctorResponse updateDoctor(UUID id, DoctorRequest request) {
        Doctor doctor = getDoctorOrThrow(id);

        if (!doctor.getLicenseNumber().equals(request.licenseNumber())) {
            doctorRepository.findByLicenseNumber(request.licenseNumber()).ifPresent(existing -> {
                throw new DuplicateResourceException("A doctor with license number " + request.licenseNumber() + " already exists");
            });
        }

        doctor.setFullName(request.fullName());
        doctor.setLicenseNumber(request.licenseNumber());
        doctor.setContactInfo(request.contactInfo());

        return toResponse(doctorRepository.save(doctor));
    }

    @Override
    public DoctorResponse getDoctorById(UUID id) {
        return toResponse(getDoctorOrThrow(id));
    }

    @Override
    public List<DoctorResponse> getAllDoctors() {
        return doctorRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    public List<DoctorResponse> searchDoctorsByName(String name) {
        return doctorRepository.findByFullNameContainingIgnoreCase(name).stream().map(this::toResponse).toList();
    }

    private Doctor getDoctorOrThrow(UUID id) {
        return doctorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + id));
    }

    private DoctorResponse toResponse(Doctor doctor) {
        return new DoctorResponse(doctor.getId(), doctor.getFullName(), doctor.getLicenseNumber(), doctor.getContactInfo());
    }
}

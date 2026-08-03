package com.app.pharmacy.repository;

import com.app.pharmacy.domain.entity.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DoctorRepository extends JpaRepository<Doctor, UUID> {

    Optional<Doctor> findByLicenseNumber(String licenseNumber);

    java.util.List<Doctor> findByFullNameContainingIgnoreCase(String fullName);
}

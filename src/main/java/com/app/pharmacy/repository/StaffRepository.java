package com.app.pharmacy.repository;

import com.app.pharmacy.domain.entity.Staff;
import com.app.pharmacy.domain.entity.enums.StaffRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffRepository extends JpaRepository<Staff, UUID> {

    /** Login lookup — email is the username (Rule 10). */
    Optional<Staff> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByLicenseNumber(String licenseNumber);

    List<Staff> findByRole(StaffRole role);

    List<Staff> findByActiveStatusTrue();
}

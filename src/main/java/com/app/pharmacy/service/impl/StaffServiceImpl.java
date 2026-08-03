package com.app.pharmacy.service.impl;

import com.app.pharmacy.domain.dtos.request.StaffCreateRequest;
import com.app.pharmacy.domain.dtos.request.StaffUpdateRequest;
import com.app.pharmacy.domain.dtos.response.StaffCreateResponse;
import com.app.pharmacy.domain.dtos.response.StaffResponse;
import com.app.pharmacy.domain.entity.Staff;
import com.app.pharmacy.exception.DuplicateResourceException;
import com.app.pharmacy.exception.ResourceNotFoundException;
import com.app.pharmacy.repository.StaffRepository;
import com.app.pharmacy.service.StaffService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StaffServiceImpl implements StaffService {

    private static final String TEMP_PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
    private static final int TEMP_PASSWORD_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StaffRepository staffRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public StaffCreateResponse createStaff(StaffCreateRequest request) {
        if (staffRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("A staff account with email " + request.email() + " already exists");
        }
        if (staffRepository.existsByPhoneNumber(request.phoneNumber())) {
            throw new DuplicateResourceException("A staff account with phone number " + request.phoneNumber() + " already exists");
        }
        if (request.licenseNumber() != null && !request.licenseNumber().isBlank()
                && staffRepository.existsByLicenseNumber(request.licenseNumber())) {
            throw new DuplicateResourceException("A staff account with license number " + request.licenseNumber() + " already exists");
        }

        String tempPassword = request.generatePassword() ? generateTempPassword() : request.tempPassword();

        Staff staff = Staff.builder()
                .fullName(request.fullName())
                .role(request.role())
                .licenseNumber(request.licenseNumber())
                .phoneNumber(request.phoneNumber())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(tempPassword))
                .mustResetPassword(true) // always forced TRUE on creation (Rule 13), regardless of path
                .hireDate(request.hireDate())
                .activeStatus(true)
                .build();

        Staff saved = staffRepository.save(staff);

        // tempPassword is returned exactly once here — only the BCrypt hash persists after this.
        return new StaffCreateResponse(toResponse(saved), tempPassword);
    }

    @Override
    @Transactional
    public StaffResponse updateStaff(UUID id, StaffUpdateRequest request) {
        Staff staff = getStaffOrThrow(id);

        if (!staff.getEmail().equals(request.email()) && staffRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("A staff account with email " + request.email() + " already exists");
        }
        if (!staff.getPhoneNumber().equals(request.phoneNumber()) && staffRepository.existsByPhoneNumber(request.phoneNumber())) {
            throw new DuplicateResourceException("A staff account with phone number " + request.phoneNumber() + " already exists");
        }

        staff.setFullName(request.fullName());
        staff.setRole(request.role());
        staff.setLicenseNumber(request.licenseNumber());
        staff.setPhoneNumber(request.phoneNumber());
        staff.setEmail(request.email());
        staff.setActiveStatus(request.activeStatus());
        // password_hash / must_reset_password are never touched here — only via auth/change-password.

        return toResponse(staffRepository.save(staff));
    }

    @Override
    @Transactional
    public StaffResponse deactivateStaff(UUID id) {
        Staff staff = getStaffOrThrow(id);
        staff.setActiveStatus(false);
        return toResponse(staffRepository.save(staff));
    }

    @Override
    public StaffResponse getStaffById(UUID id) {
        return toResponse(getStaffOrThrow(id));
    }

    @Override
    public List<StaffResponse> getAllStaff() {
        return staffRepository.findAll().stream().map(this::toResponse).toList();
    }

    private Staff getStaffOrThrow(UUID id) {
        return staffRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found: " + id));
    }

    private String generateTempPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            sb.append(TEMP_PASSWORD_ALPHABET.charAt(RANDOM.nextInt(TEMP_PASSWORD_ALPHABET.length())));
        }
        return sb.toString();
    }

    private StaffResponse toResponse(Staff staff) {
        return new StaffResponse(
                staff.getId(),
                staff.getFullName(),
                staff.getRole(),
                staff.getLicenseNumber(),
                staff.getPhoneNumber(),
                staff.getEmail(),
                staff.getMustResetPassword(),
                staff.getHireDate(),
                staff.getActiveStatus()
        );
    }
}

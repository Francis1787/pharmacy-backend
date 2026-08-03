package com.app.pharmacy.service.impl;

import com.app.pharmacy.domain.dtos.request.ChangePasswordRequest;
import com.app.pharmacy.domain.dtos.request.LoginRequest;
import com.app.pharmacy.domain.dtos.response.LoginResponse;
import com.app.pharmacy.domain.entity.Staff;
import com.app.pharmacy.exception.InvalidCredentialsException;
import com.app.pharmacy.exception.ResourceNotFoundException;
import com.app.pharmacy.repository.StaffRepository;
import com.app.pharmacy.security.JwtTokenProvider;
import com.app.pharmacy.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final StaffRepository staffRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public LoginResponse login(LoginRequest request) {
        Staff staff = staffRepository.findByEmail(request.email())
                // Same generic message whether the email is unknown or the password is
                // wrong — don't let a login attempt reveal which accounts exist.
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!Boolean.TRUE.equals(staff.getActiveStatus())) {
            throw new InvalidCredentialsException("This account has been deactivated");
        }

        if (!passwordEncoder.matches(request.password(), staff.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        String token = jwtTokenProvider.generateToken(staff);

        return new LoginResponse(
                token,
                staff.getId(),
                staff.getFullName(),
                staff.getRole(),
                Boolean.TRUE.equals(staff.getMustResetPassword())
        );
    }

    @Override
    @Transactional
    public void changePassword(UUID staffId, ChangePasswordRequest request) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found: " + staffId));

        if (!passwordEncoder.matches(request.currentPassword(), staff.getPasswordHash())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }

        staff.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        staff.setMustResetPassword(false); // Rule 13: cleared once the staff member sets their own password
        staffRepository.save(staff);
    }
}

package com.app.pharmacy.service;

import com.app.pharmacy.domain.dtos.request.ChangePasswordRequest;
import com.app.pharmacy.domain.dtos.request.LoginRequest;
import com.app.pharmacy.domain.dtos.response.LoginResponse;

import java.util.UUID;

public interface AuthService {

    /**
     * Verifies email/password against Staff.password_hash (Rule 10) and,
     * if valid, issues a token via JwtTokenProvider. Throws
     * InvalidCredentialsException on unknown email, wrong password, or an
     * inactive account.
     */
    LoginResponse login(LoginRequest request);

    /**
     * Verifies currentPassword, re-hashes newPassword, and clears
     * must_reset_password (Rule 13). staffId comes from the authenticated
     * session, not the request body.
     */
    void changePassword(UUID staffId, ChangePasswordRequest request);
}

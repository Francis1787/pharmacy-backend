package com.app.pharmacy.controller;

import com.app.pharmacy.domain.dtos.common.ApiResponse;
import com.app.pharmacy.domain.dtos.request.ChangePasswordRequest;
import com.app.pharmacy.domain.dtos.request.LoginRequest;
import com.app.pharmacy.domain.dtos.response.LoginResponse;
import com.app.pharmacy.security.CustomUserDetails;
import com.app.pharmacy.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** Public — the one endpoint reachable without a token (see SecurityConfig). */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    /**
     * Authenticated — staffId comes from the token via @AuthenticationPrincipal,
     * never from the request body, so a staff member can only ever change
     * their own password. Returns an explicit success message rather than
     * a bare 204 — if this call throws (wrong current password), that's
     * caught by GlobalExceptionHandler and returned as its own ApiResponse
     * with success=false, so the caller always gets a clear answer either way.
     */
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        authService.changePassword(principal.getStaffId(), request);
        return ResponseEntity.ok(ApiResponse.success(null, "Password changed successfully"));
    }
}

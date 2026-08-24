package com.app.pharmacy.controller;

import com.app.pharmacy.config.PasswordEncoderConfig;
import com.app.pharmacy.config.SecurityConfig;
import com.app.pharmacy.domain.dtos.request.ChangePasswordRequest;
import com.app.pharmacy.domain.dtos.request.LoginRequest;
import com.app.pharmacy.domain.dtos.response.LoginResponse;
import com.app.pharmacy.domain.entity.Staff;
import com.app.pharmacy.domain.entity.enums.StaffRole;
import com.app.pharmacy.exception.InvalidCredentialsException;
import com.app.pharmacy.security.*;
import com.app.pharmacy.service.AuthService;
import com.app.pharmacy.support.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The login endpoint is the only unauthenticated route in the application,
 * which makes it the whole attack surface for an anonymous caller. What
 * matters here is that it is reachable without a token, that failures do not
 * leak which accounts exist, and that change-password can only ever act on
 * the caller's own account.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, PasswordEncoderConfig.class, JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost:8081",
        "app.jwt.secret=test-secret-key-that-is-at-least-32-bytes-long-for-hs256",
        "app.jwt.expiration-ms=3600000"
})
@DisplayName("AuthController — the one public endpoint")
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;

    /** Built locally: the Boot 4 WebMvc slice does not expose an ObjectMapper bean. */
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @MockitoBean private AuthService authService;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    private Staff staff;

    @BeforeEach
    void setUp() {
        staff = TestFixtures.pharmacist();
    }

    private RequestPostProcessor asStaff() {
        return user(new CustomUserDetails(staff));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    @Nested
    @DisplayName("Login")
    class Login {

        @Test
        @WithAnonymousUser
        @DisplayName("is reachable without a token and returns the token plus profile")
        void loginIsPublic() throws Exception {
            when(authService.login(any())).thenReturn(new LoginResponse(
                    "signed.jwt.token", staff.getId(), staff.getFullName(), StaffRole.Pharmacist, false));

            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json(new LoginRequest(staff.getEmail(), "Correct-Horse-9"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.token").value("signed.jwt.token"))
                    .andExpect(jsonPath("$.data.role").value("Pharmacist"));
        }

        @Test
        @WithAnonymousUser
        @DisplayName("signals the Rule 13 forced reset in the login payload")
        void surfacesForcedReset() throws Exception {
            when(authService.login(any())).thenReturn(new LoginResponse(
                    "signed.jwt.token", staff.getId(), staff.getFullName(), StaffRole.Pharmacist, true));

            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json(new LoginRequest(staff.getEmail(), "Temp-Pass-1"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.mustResetPassword").value(true));
        }

        @Test
        @WithAnonymousUser
        @DisplayName("bad credentials return 401 with a generic message and no token")
        void badCredentialsAre401() throws Exception {
            when(authService.login(any())).thenThrow(new InvalidCredentialsException("Invalid email or password"));

            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json(new LoginRequest(staff.getEmail(), "wrong"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Invalid email or password"))
                    .andExpect(jsonPath("$.token").doesNotExist());
        }

        @Test
        @WithAnonymousUser
        @DisplayName("a malformed email is rejected as 400 before any lookup happens")
        void malformedEmailIs400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json(new LoginRequest("not-an-email", "whatever"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.email").exists());

            verify(authService, never()).login(any());
        }

        @Test
        @WithAnonymousUser
        @DisplayName("a blank password is rejected as 400")
        void blankPasswordIs400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json(new LoginRequest(staff.getEmail(), ""))))
                    .andExpect(status().isBadRequest());

            verify(authService, never()).login(any());
        }
    }

    @Nested
    @DisplayName("Change password")
    class ChangePassword {

        @Test
        @WithAnonymousUser
        @DisplayName("is NOT public — an anonymous caller gets 401")
        void requiresAuthentication() throws Exception {
            mockMvc.perform(post("/api/v1/auth/change-password").contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ChangePasswordRequest("old-pass", "new-long-pass"))))
                    .andExpect(status().isUnauthorized());

            verify(authService, never()).changePassword(any(), any());
        }

        @Test
        @DisplayName("acts on the caller's own staff id taken from the token")
        void actsOnAuthenticatedStaffOnly() throws Exception {
            mockMvc.perform(post("/api/v1/auth/change-password").with(asStaff())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ChangePasswordRequest("old-pass", "new-long-pass"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Password changed successfully"));

            verify(authService).changePassword(eq(staff.getId()), any(ChangePasswordRequest.class));
            verify(authService, never()).changePassword(
                    argThat(id -> !id.equals(staff.getId())), any());
        }

        @Test
        @DisplayName("a new password under 8 characters is rejected as 400")
        void enforcesMinimumLength() throws Exception {
            mockMvc.perform(post("/api/v1/auth/change-password").with(asStaff())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ChangePasswordRequest("old-pass", "short"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.newPassword").exists());

            verify(authService, never()).changePassword(any(), any());
        }

        @Test
        @DisplayName("a wrong current password comes back as 401, not 500")
        void wrongCurrentPasswordIs401() throws Exception {
            doThrow(new InvalidCredentialsException("Current password is incorrect"))
                    .when(authService).changePassword(any(), any());

            mockMvc.perform(post("/api/v1/auth/change-password").with(asStaff())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ChangePasswordRequest("wrong-current", "new-long-pass"))))
                    .andExpect(status().isUnauthorized());
        }
    }

}

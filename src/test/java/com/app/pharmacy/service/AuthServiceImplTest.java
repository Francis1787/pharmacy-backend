package com.app.pharmacy.service;

import com.app.pharmacy.domain.dtos.request.ChangePasswordRequest;
import com.app.pharmacy.domain.dtos.request.LoginRequest;
import com.app.pharmacy.domain.dtos.response.LoginResponse;
import com.app.pharmacy.domain.entity.Staff;
import com.app.pharmacy.exception.InvalidCredentialsException;
import com.app.pharmacy.exception.ResourceNotFoundException;
import com.app.pharmacy.repository.StaffRepository;
import com.app.pharmacy.security.JwtTokenProvider;
import com.app.pharmacy.service.impl.AuthServiceImpl;
import com.app.pharmacy.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Authentication (Rule 10) and the forced first-login password reset (Rule 13).
 *
 * A real {@link BCryptPasswordEncoder} is used rather than a mock — password
 * comparison is the behaviour under test, and mocking matches() would only
 * assert that the test's own stub was called.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthService — login and password change")
class AuthServiceImplTest {

    private static final String CORRECT_PASSWORD = "Correct-Horse-9";

    @Mock private StaffRepository staffRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AuthServiceImpl authService;
    private Staff staff;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(staffRepository, passwordEncoder, jwtTokenProvider);

        staff = TestFixtures.pharmacist();
        staff.setPasswordHash(passwordEncoder.encode(CORRECT_PASSWORD));

        when(staffRepository.findByEmail(staff.getEmail())).thenReturn(Optional.of(staff));
        when(staffRepository.findById(staff.getId())).thenReturn(Optional.of(staff));
        when(staffRepository.save(any(Staff.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtTokenProvider.generateToken(any(Staff.class))).thenReturn("signed.jwt.token");
    }

    @Nested
    @DisplayName("Login")
    class Login {

        @Test
        @DisplayName("issues a token and profile on correct credentials")
        void succeedsWithCorrectCredentials() {
            LoginResponse response = authService.login(new LoginRequest(staff.getEmail(), CORRECT_PASSWORD));

            assertThat(response.token()).isEqualTo("signed.jwt.token");
            assertThat(response.staffId()).isEqualTo(staff.getId());
            assertThat(response.role()).isEqualTo(staff.getRole());
        }

        @Test
        @DisplayName("rejects a wrong password without issuing a token")
        void rejectsWrongPassword() {
            assertThatThrownBy(() -> authService.login(new LoginRequest(staff.getEmail(), "not-the-password")))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage("Invalid email or password");

            verify(jwtTokenProvider, never()).generateToken(any());
        }

        @Test
        @DisplayName("rejects an unknown email with the same generic message")
        void rejectsUnknownEmail() {
            when(staffRepository.findByEmail("ghost@pharmacy.test")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@pharmacy.test", CORRECT_PASSWORD)))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage("Invalid email or password");
        }

        @Test
        @DisplayName("rejects a deactivated account even with the right password")
        void rejectsDeactivatedAccount() {
            staff.setActiveStatus(false);

            assertThatThrownBy(() -> authService.login(new LoginRequest(staff.getEmail(), CORRECT_PASSWORD)))
                    .isInstanceOf(InvalidCredentialsException.class);

            verify(jwtTokenProvider, never()).generateToken(any());
        }

        /**
         * DEFECT-02. login() checks activeStatus BEFORE verifying the password,
         * and returns a distinct "This account has been deactivated" message.
         * An attacker supplying any password can therefore tell a real
         * (deactivated) account apart from an address that was never
         * registered — the exact user enumeration the service's own comment
         * says it is avoiding.
         *
         * The fix is to verify the password first and return the generic
         * message for a deactivated account. This test asserts that required
         * behaviour and fails against the current code.
         */
        @Test
        @Tag("known-defect")
        @DisplayName("does not reveal that a deactivated account exists")
        void deactivatedAccountIsIndistinguishableFromUnknownEmail() {
            staff.setActiveStatus(false);
            when(staffRepository.findByEmail("ghost@pharmacy.test")).thenReturn(Optional.empty());

            String deactivatedMessage = messageFrom(staff.getEmail(), "guessed-password");
            String unknownMessage = messageFrom("ghost@pharmacy.test", "guessed-password");

            assertThat(deactivatedMessage)
                    .as("a wrong-password attempt must look identical for a deactivated and a non-existent account")
                    .isEqualTo(unknownMessage);
        }

        private String messageFrom(String email, String password) {
            try {
                authService.login(new LoginRequest(email, password));
                return "<no exception>";
            } catch (RuntimeException e) {
                return e.getMessage();
            }
        }

        @Test
        @DisplayName("surfaces mustResetPassword so the client can force the Rule 13 reset screen")
        void surfacesMustResetPasswordFlag() {
            staff.setMustResetPassword(true);

            LoginResponse response = authService.login(new LoginRequest(staff.getEmail(), CORRECT_PASSWORD));

            assertThat(response.mustResetPassword()).isTrue();
        }

        @Test
        @DisplayName("reports mustResetPassword false once the reset has happened")
        void reportsResetNotRequired() {
            staff.setMustResetPassword(false);

            LoginResponse response = authService.login(new LoginRequest(staff.getEmail(), CORRECT_PASSWORD));

            assertThat(response.mustResetPassword()).isFalse();
        }
    }

    @Nested
    @DisplayName("Change password")
    class ChangePassword {

        @Test
        @DisplayName("re-hashes the new password and never stores it in clear")
        void storesHashedPassword() {
            authService.changePassword(staff.getId(),
                    new ChangePasswordRequest(CORRECT_PASSWORD, "Brand-New-Pass-1"));

            assertThat(staff.getPasswordHash()).doesNotContain("Brand-New-Pass-1");
            assertThat(passwordEncoder.matches("Brand-New-Pass-1", staff.getPasswordHash())).isTrue();
        }

        @Test
        @DisplayName("clears mustResetPassword once the staff member sets their own password (Rule 13)")
        void clearsMustResetFlag() {
            staff.setMustResetPassword(true);

            authService.changePassword(staff.getId(),
                    new ChangePasswordRequest(CORRECT_PASSWORD, "Brand-New-Pass-1"));

            assertThat(staff.getMustResetPassword()).isFalse();
        }

        @Test
        @DisplayName("refuses when the current password is wrong and leaves the hash untouched")
        void refusesWrongCurrentPassword() {
            String originalHash = staff.getPasswordHash();

            assertThatThrownBy(() -> authService.changePassword(staff.getId(),
                    new ChangePasswordRequest("wrong-current", "Brand-New-Pass-1")))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessageContaining("Current password is incorrect");

            assertThat(staff.getPasswordHash()).isEqualTo(originalHash);
            verify(staffRepository, never()).save(any());
        }

        @Test
        @DisplayName("404s for an unknown staff id")
        void unknownStaff() {
            UUID unknown = UUID.randomUUID();
            when(staffRepository.findById(unknown)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.changePassword(unknown,
                    new ChangePasswordRequest(CORRECT_PASSWORD, "Brand-New-Pass-1")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}

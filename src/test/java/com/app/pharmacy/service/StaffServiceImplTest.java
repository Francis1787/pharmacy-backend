package com.app.pharmacy.service;

import com.app.pharmacy.domain.dtos.request.StaffCreateRequest;
import com.app.pharmacy.domain.dtos.request.StaffUpdateRequest;
import com.app.pharmacy.domain.dtos.response.StaffCreateResponse;
import com.app.pharmacy.domain.dtos.response.StaffResponse;
import com.app.pharmacy.domain.entity.Staff;
import com.app.pharmacy.domain.entity.enums.StaffRole;
import com.app.pharmacy.exception.DuplicateResourceException;
import com.app.pharmacy.exception.ResourceNotFoundException;
import com.app.pharmacy.repository.StaffRepository;
import com.app.pharmacy.service.impl.StaffServiceImpl;
import com.app.pharmacy.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Staff account creation (Rule 13) and administration.
 *
 * The security-relevant properties here are: no clear-text password ever
 * reaches the database, the temp password is disclosed exactly once, and
 * must_reset_password is forced true on creation no matter which path
 * produced the password.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StaffService — account provisioning")
class StaffServiceImplTest {

    @Mock private StaffRepository staffRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private StaffServiceImpl staffService;

    @BeforeEach
    void setUp() {
        staffService = new StaffServiceImpl(staffRepository, passwordEncoder);
        when(staffRepository.existsByEmail(anyString())).thenReturn(false);
        when(staffRepository.existsByPhoneNumber(anyString())).thenReturn(false);
        when(staffRepository.existsByLicenseNumber(anyString())).thenReturn(false);
        when(staffRepository.save(any(Staff.class))).thenAnswer(inv -> {
            Staff s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(UUID.randomUUID());
            }
            return s;
        });
    }

    private StaffCreateRequest technicianRequest(boolean generate, String tempPassword) {
        return new StaffCreateRequest("Kojo Antwi", StaffRole.Technician, null,
                "+233201234567", "kojo.antwi@pharmacy.test", LocalDate.now(), generate, tempPassword);
    }

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("forces mustResetPassword true regardless of how the password was set")
        void forcesPasswordReset() {
            StaffCreateResponse generated = staffService.createStaff(technicianRequest(true, null));
            StaffCreateResponse supplied = staffService.createStaff(technicianRequest(false, "Supplied-Pass-1"));

            assertThat(generated.staff().mustResetPassword()).isTrue();
            assertThat(supplied.staff().mustResetPassword()).isTrue();
        }

        @Test
        @DisplayName("persists only a BCrypt hash, never the clear-text password")
        void neverPersistsClearTextPassword() {
            ArgumentCaptor<Staff> saved = ArgumentCaptor.forClass(Staff.class);
            staffService.createStaff(technicianRequest(false, "Supplied-Pass-1"));
            verify(staffRepository).save(saved.capture());

            String hash = saved.getValue().getPasswordHash();
            assertThat(hash).doesNotContain("Supplied-Pass-1").startsWith("$2");
            assertThat(passwordEncoder.matches("Supplied-Pass-1", hash)).isTrue();
        }

        @Test
        @DisplayName("returns the generated temp password once, and it matches the stored hash")
        void returnsGeneratedTempPasswordOnce() {
            ArgumentCaptor<Staff> saved = ArgumentCaptor.forClass(Staff.class);
            StaffCreateResponse response = staffService.createStaff(technicianRequest(true, null));
            verify(staffRepository).save(saved.capture());

            assertThat(response.tempPassword()).isNotBlank().hasSize(12);
            assertThat(passwordEncoder.matches(response.tempPassword(), saved.getValue().getPasswordHash())).isTrue();
        }

        @Test
        @DisplayName("generates a different temp password each time")
        void generatesDistinctTempPasswords() {
            String first = staffService.createStaff(technicianRequest(true, null)).tempPassword();
            String second = staffService.createStaff(technicianRequest(true, null)).tempPassword();

            assertThat(first).isNotEqualTo(second);
        }

        @Test
        @DisplayName("never exposes the password hash on the response DTO")
        void responseCarriesNoHash() {
            StaffResponse staff = staffService.createStaff(technicianRequest(true, null)).staff();

            assertThat(staff.toString()).doesNotContain("$2a$").doesNotContain("passwordHash");
        }

        @Test
        @DisplayName("activates the new account by default")
        void activatesByDefault() {
            assertThat(staffService.createStaff(technicianRequest(true, null)).staff().activeStatus()).isTrue();
        }

        @Test
        @DisplayName("409s on a duplicate email")
        void rejectsDuplicateEmail() {
            when(staffRepository.existsByEmail("kojo.antwi@pharmacy.test")).thenReturn(true);

            assertThatThrownBy(() -> staffService.createStaff(technicianRequest(true, null)))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("email");
        }

        @Test
        @DisplayName("409s on a duplicate phone number")
        void rejectsDuplicatePhone() {
            when(staffRepository.existsByPhoneNumber("+233201234567")).thenReturn(true);

            assertThatThrownBy(() -> staffService.createStaff(technicianRequest(true, null)))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("phone number");
        }

        @Test
        @DisplayName("409s on a duplicate pharmacist license number")
        void rejectsDuplicateLicense() {
            when(staffRepository.existsByLicenseNumber("PH-2201")).thenReturn(true);

            StaffCreateRequest request = new StaffCreateRequest("Abena Darko", StaffRole.Pharmacist, "PH-2201",
                    "+233209999999", "abena.darko@pharmacy.test", LocalDate.now(), true, null);

            assertThatThrownBy(() -> staffService.createStaff(request))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("license number");
        }
    }

    @Nested
    @DisplayName("Update and deactivation")
    class Administration {

        private Staff existing;

        @BeforeEach
        void seedExisting() {
            existing = TestFixtures.technician();
            when(staffRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        }

        @Test
        @DisplayName("allows an update that keeps the same email and phone")
        void allowsUnchangedContactDetails() {
            when(staffRepository.existsByEmail(existing.getEmail())).thenReturn(true);
            when(staffRepository.existsByPhoneNumber(existing.getPhoneNumber())).thenReturn(true);

            StaffResponse response = staffService.updateStaff(existing.getId(), new StaffUpdateRequest(
                    "Kofi B. Boateng", StaffRole.Technician, null,
                    existing.getPhoneNumber(), existing.getEmail(), true));

            assertThat(response.fullName()).isEqualTo("Kofi B. Boateng");
        }

        @Test
        @DisplayName("409s when moving to an email another account already owns")
        void rejectsEmailTakenByAnother() {
            when(staffRepository.existsByEmail("taken@pharmacy.test")).thenReturn(true);

            assertThatThrownBy(() -> staffService.updateStaff(existing.getId(), new StaffUpdateRequest(
                    existing.getFullName(), StaffRole.Technician, null,
                    existing.getPhoneNumber(), "taken@pharmacy.test", true)))
                    .isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        @DisplayName("never touches the password hash or the reset flag on update")
        void leavesCredentialsAlone() {
            String originalHash = existing.getPasswordHash();
            existing.setMustResetPassword(true);

            staffService.updateStaff(existing.getId(), new StaffUpdateRequest(
                    "Renamed Person", StaffRole.Technician, null,
                    existing.getPhoneNumber(), existing.getEmail(), true));

            assertThat(existing.getPasswordHash()).isEqualTo(originalHash);
            assertThat(existing.getMustResetPassword()).isTrue();
        }

        @Test
        @DisplayName("deactivation clears activeStatus so the account can no longer authenticate")
        void deactivates() {
            StaffResponse response = staffService.deactivateStaff(existing.getId());

            assertThat(response.activeStatus()).isFalse();
            assertThat(existing.getActiveStatus()).isFalse();
        }

        @Test
        @DisplayName("404s on an unknown staff id")
        void unknownStaff() {
            UUID unknown = UUID.randomUUID();
            when(staffRepository.findById(unknown)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> staffService.deactivateStaff(unknown))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        /**
         * DEFECT-03. createStaff() checks existsByLicenseNumber, but
         * updateStaff() does not — promoting a Technician to Pharmacist (or
         * correcting a licence) can be given a licence number that already
         * belongs to another pharmacist. The DB UNIQUE constraint on
         * staff.license_number then rejects the flush as a raw 500 rather
         * than the intended 409, and two staff rows race for one licence.
         */
        @Test
        @Tag("known-defect")
        @DisplayName("409s when an update claims another pharmacist's licence number")
        void rejectsLicenceTakenByAnother() {
            when(staffRepository.existsByLicenseNumber("PH-EXISTING")).thenReturn(true);

            assertThatThrownBy(() -> staffService.updateStaff(existing.getId(), new StaffUpdateRequest(
                    existing.getFullName(), StaffRole.Pharmacist, "PH-EXISTING",
                    existing.getPhoneNumber(), existing.getEmail(), true)))
                    .as("licence uniqueness must be checked on update as it is on create")
                    .isInstanceOf(DuplicateResourceException.class);
        }
    }
}

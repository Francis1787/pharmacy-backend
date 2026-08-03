package com.app.pharmacy.domain.entity;

import com.app.pharmacy.domain.entity.enums.StaffRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A pharmacy staff member. Staff.email is the login username;
 * Staff.role drives authorization (Rule 10).
 *
 * must_reset_password starts TRUE on account creation (Rule 13) —
 * the app must check this immediately after login and force a
 * password-change screen before any other page is reachable.
 */
@Entity
@Table(name = "staff")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Staff {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private StaffRole role;

    /** Nullable — only Pharmacist accounts carry a license number; UNIQUE when present. */
    @Column(name = "license_number", unique = true, length = 50)
    private String licenseNumber;

    @Column(name = "phone_number", nullable = false, unique = true, length = 20)
    private String phoneNumber;

    /** Login username. */
    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    /** BCrypt hash only — the application layer hashes before this is ever set. */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "must_reset_password", nullable = false)
    @Builder.Default
    private Boolean mustResetPassword = true;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Column(name = "active_status", nullable = false)
    @Builder.Default
    private Boolean activeStatus = true;
}

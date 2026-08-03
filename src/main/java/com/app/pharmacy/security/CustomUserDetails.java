package com.app.pharmacy.security;

import com.app.pharmacy.domain.entity.Staff;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Adapts Staff to Spring Security's UserDetails. The single granted
 * authority is "ROLE_" + role name in upper case (e.g. ROLE_PHARMACIST,
 * ROLE_TECHNICIAN, ROLE_ADMIN) so controller-layer @PreAuthorize checks
 * can use hasRole("PHARMACIST") etc. — matching Staff.role from the DB,
 * not a separately maintained permission list.
 *
 * isEnabled() reflects Staff.active_status — a deactivated account is
 * rejected at authentication time, not just hidden from listings.
 */
public class CustomUserDetails implements UserDetails {

    private final Staff staff;

    public CustomUserDetails(Staff staff) {
        this.staff = staff;
    }

    public UUID getStaffId() {
        return staff.getId();
    }

    public Staff getStaff() {
        return staff;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + staff.getRole().name().toUpperCase()));
    }

    @Override
    public String getPassword() {
        return staff.getPasswordHash();
    }

    /** Email is the login username (Rule 10), not a separate username field. */
    @Override
    public String getUsername() {
        return staff.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(staff.getActiveStatus());
    }
}

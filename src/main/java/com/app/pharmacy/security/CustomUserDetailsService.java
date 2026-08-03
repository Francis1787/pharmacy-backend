package com.app.pharmacy.security;

import com.app.pharmacy.domain.entity.Staff;
import com.app.pharmacy.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final StaffRepository staffRepository;

    /** Used by the login flow (Spring Security's DaoAuthenticationProvider expects this signature). */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Staff staff = staffRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No staff account with email: " + email));
        return new CustomUserDetails(staff);
    }

    /**
     * Used by JwtAuthenticationFilter — the token's subject claim is
     * Staff.id, not email, so per-request re-authentication loads by ID
     * directly rather than round-tripping through loadUserByUsername.
     */
    public UserDetails loadUserByStaffId(UUID staffId) throws UsernameNotFoundException {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new UsernameNotFoundException("No staff account with id: " + staffId));
        return new CustomUserDetails(staff);
    }
}

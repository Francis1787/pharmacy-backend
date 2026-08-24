package com.app.pharmacy.security;

import com.app.pharmacy.domain.entity.Staff;
import com.app.pharmacy.domain.entity.enums.StaffRole;
import com.app.pharmacy.repository.StaffRepository;
import com.app.pharmacy.support.TestFixtures;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * The three pieces that turn a bearer token into an authenticated principal.
 * Between them they decide, on every single request, who the caller is and
 * what role they carry — and nothing downstream re-checks that decision.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Security components — identity resolution")
class SecurityComponentsTest {

    @Nested
    @DisplayName("CustomUserDetails — adapting Staff to Spring Security")
    class Principal {

        @ParameterizedTest(name = "{0} maps to {1}")
        @CsvSource({
                "Pharmacist, ROLE_PHARMACIST",
                "Technician, ROLE_TECHNICIAN",
                "Admin,      ROLE_ADMIN"
        })
        @DisplayName("grants exactly one ROLE_ authority, upper-cased")
        void mapsRoleToAuthority(StaffRole role, String expectedAuthority) {
            Staff staff = TestFixtures.pharmacist();
            staff.setRole(role);

            var authorities = new CustomUserDetails(staff).getAuthorities();

            assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                    .containsExactly(expectedAuthority);
        }

        @Test
        @DisplayName("uses email as the username (Rule 10), not the full name")
        void usernameIsEmail() {
            Staff staff = TestFixtures.pharmacist();

            assertThat(new CustomUserDetails(staff).getUsername()).isEqualTo(staff.getEmail());
        }

        @Test
        @DisplayName("an active account is enabled")
        void activeAccountIsEnabled() {
            Staff staff = TestFixtures.pharmacist();
            staff.setActiveStatus(true);

            assertThat(new CustomUserDetails(staff).isEnabled()).isTrue();
        }

        @Test
        @DisplayName("a deactivated account is disabled")
        void deactivatedAccountIsDisabled() {
            Staff staff = TestFixtures.pharmacist();
            staff.setActiveStatus(false);

            assertThat(new CustomUserDetails(staff).isEnabled()).isFalse();
        }

        @Test
        @DisplayName("a null activeStatus is treated as disabled, not enabled")
        void nullActiveStatusIsDisabled() {
            Staff staff = TestFixtures.pharmacist();
            staff.setActiveStatus(null);

            assertThat(new CustomUserDetails(staff).isEnabled())
                    .as("an ambiguous account state must fail closed")
                    .isFalse();
        }

        @Test
        @DisplayName("exposes the staff id so controllers never take it from the request body")
        void exposesStaffId() {
            Staff staff = TestFixtures.pharmacist();

            assertThat(new CustomUserDetails(staff).getStaffId()).isEqualTo(staff.getId());
        }

        @Test
        @DisplayName("the remaining account flags are open — only activeStatus gates access")
        void otherAccountFlagsAreOpen() {
            CustomUserDetails principal = new CustomUserDetails(TestFixtures.pharmacist());

            assertThat(principal.isAccountNonExpired()).isTrue();
            assertThat(principal.isAccountNonLocked()).isTrue();
            assertThat(principal.isCredentialsNonExpired()).isTrue();
        }
    }

    @Nested
    @DisplayName("CustomUserDetailsService — loading the current Staff row")
    class Loading {

        @Mock private StaffRepository staffRepository;

        private CustomUserDetailsService service;
        private Staff staff;

        @BeforeEach
        void setUp() {
            service = new CustomUserDetailsService(staffRepository);
            staff = TestFixtures.pharmacist();
            when(staffRepository.findByEmail(staff.getEmail())).thenReturn(Optional.of(staff));
            when(staffRepository.findById(staff.getId())).thenReturn(Optional.of(staff));
        }

        @Test
        @DisplayName("loads by email for the username/password path")
        void loadsByEmail() {
            UserDetails details = service.loadUserByUsername(staff.getEmail());

            assertThat(details.getUsername()).isEqualTo(staff.getEmail());
        }

        @Test
        @DisplayName("loads by primary key for the JWT path")
        void loadsByStaffId() {
            UserDetails details = service.loadUserByStaffId(staff.getId());

            assertThat(((CustomUserDetails) details).getStaffId()).isEqualTo(staff.getId());
        }

        @Test
        @DisplayName("throws for an unknown email rather than returning null")
        void unknownEmail() {
            when(staffRepository.findByEmail("ghost@pharmacy.test")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.loadUserByUsername("ghost@pharmacy.test"))
                    .isInstanceOf(UsernameNotFoundException.class);
        }

        @Test
        @DisplayName("throws for a staff id that no longer exists")
        void unknownStaffId() {
            UUID ghost = UUID.randomUUID();
            when(staffRepository.findById(ghost)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.loadUserByStaffId(ghost))
                    .isInstanceOf(UsernameNotFoundException.class);
        }

        @Test
        @DisplayName("still loads a deactivated account, leaving the enabled check to the caller")
        void loadsDeactivatedAccount() {
            staff.setActiveStatus(false);

            assertThat(service.loadUserByStaffId(staff.getId()).isEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("JwtAuthenticationFilter — populating the SecurityContext")
    class Filter {

        @Mock private JwtTokenProvider jwtTokenProvider;
        @Mock private CustomUserDetailsService userDetailsService;
        @Mock private FilterChain filterChain;

        private JwtAuthenticationFilter filter;
        private MockHttpServletRequest request;
        private MockHttpServletResponse response;
        private Staff staff;

        @BeforeEach
        void setUp() {
            filter = new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService);
            request = new MockHttpServletRequest();
            response = new MockHttpServletResponse();
            staff = TestFixtures.pharmacist();
            SecurityContextHolder.clearContext();
        }

        @AfterEach
        void tearDown() {
            SecurityContextHolder.clearContext();
        }

        private void givenValidTokenFor(Staff s) {
            request.addHeader("Authorization", "Bearer good.token");
            when(jwtTokenProvider.validateToken("good.token")).thenReturn(true);
            when(jwtTokenProvider.getStaffIdFromToken("good.token")).thenReturn(s.getId());
            when(userDetailsService.loadUserByStaffId(s.getId())).thenReturn(new CustomUserDetails(s));
        }

        private Object currentPrincipal() {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            return auth == null ? null : auth.getPrincipal();
        }

        @Test
        @DisplayName("authenticates a request carrying a valid token")
        void authenticatesValidToken() throws Exception {
            givenValidTokenFor(staff);

            filter.doFilterInternal(request, response, filterChain);

            assertThat(currentPrincipal()).isInstanceOf(CustomUserDetails.class);
            assertThat(((CustomUserDetails) currentPrincipal()).getStaffId()).isEqualTo(staff.getId());
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("reloads the Staff row per request so a role change takes effect immediately")
        void reloadsStaffEveryRequest() throws Exception {
            givenValidTokenFor(staff);

            filter.doFilterInternal(request, response, filterChain);

            verify(userDetailsService).loadUserByStaffId(staff.getId());
        }

        @Test
        @DisplayName("leaves the context empty when no Authorization header is present")
        void noHeaderLeavesContextEmpty() throws Exception {
            filter.doFilterInternal(request, response, filterChain);

            assertThat(currentPrincipal()).isNull();
            verify(filterChain).doFilter(request, response);
            verifyNoInteractions(userDetailsService);
        }

        @Test
        @DisplayName("ignores an Authorization header that is not a Bearer token")
        void ignoresNonBearerScheme() throws Exception {
            request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

            filter.doFilterInternal(request, response, filterChain);

            assertThat(currentPrincipal()).isNull();
            verifyNoInteractions(jwtTokenProvider);
        }

        @Test
        @DisplayName("does not authenticate when the token fails validation")
        void invalidTokenLeavesContextEmpty() throws Exception {
            request.addHeader("Authorization", "Bearer forged.token");
            when(jwtTokenProvider.validateToken("forged.token")).thenReturn(false);

            filter.doFilterInternal(request, response, filterChain);

            assertThat(currentPrincipal()).isNull();
            verify(jwtTokenProvider, never()).getStaffIdFromToken(any());
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("refuses a deactivated account even with a still-valid token")
        void deactivatedAccountIsNotAuthenticated() throws Exception {
            staff.setActiveStatus(false);
            givenValidTokenFor(staff);

            filter.doFilterInternal(request, response, filterChain);

            assertThat(currentPrincipal())
                    .as("deactivation must take effect before the token expires")
                    .isNull();
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("always continues the chain, authenticated or not")
        void alwaysContinuesChain() throws Exception {
            request.addHeader("Authorization", "Bearer whatever");
            when(jwtTokenProvider.validateToken("whatever")).thenReturn(false);

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(response.getStatus())
                    .as("rejection is the filter chain's job, not this filter's")
                    .isEqualTo(200);
        }
    }
}

package com.app.pharmacy.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Runs once per request ahead of Spring Security's own auth filter.
 * Extracts "Authorization: Bearer <token>", and if it's present and
 * valid, loads the corresponding Staff fresh from the DB (via
 * CustomUserDetailsService.loadUserByStaffId — not trusted purely from
 * the token's claims) and populates the SecurityContext so @PreAuthorize
 * checks downstream have something to evaluate.
 *
 * Loading fresh on every request means a deactivated account or role
 * change takes effect immediately, not only after the token expires.
 *
 * If the header is missing/invalid, this filter does nothing and lets
 * the request continue unauthenticated — SecurityConfig's filter chain
 * is what actually rejects it later for protected routes.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null && jwtTokenProvider.validateToken(token)) {
            UUID staffId = jwtTokenProvider.getStaffIdFromToken(token);
            UserDetails userDetails = userDetailsService.loadUserByStaffId(staffId);

            if (userDetails.isEnabled()) {
                var authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            // If the account is disabled (Staff.active_status = false), the
            // SecurityContext is left empty — the request proceeds as
            // unauthenticated and gets rejected downstream like any other
            // missing-token request, rather than throwing here.
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}

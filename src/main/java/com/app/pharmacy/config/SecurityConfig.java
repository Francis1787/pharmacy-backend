package com.app.pharmacy.config;

import com.app.pharmacy.security.CustomUserDetailsService;
import com.app.pharmacy.security.JwtAccessDeniedHandler;
import com.app.pharmacy.security.JwtAuthenticationEntryPoint;
import com.app.pharmacy.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Stateless JWT security. No session, no CSRF (there's no cookie-based
 * session to forge), and coarse path-level rules here — fine-grained
 * per-role access (e.g. "only Pharmacist can approve") is enforced via
 * @PreAuthorize at the controller layer once EnableMethodSecurity is on,
 * not duplicated here as URL patterns.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    /**
     * Bound from app.cors.allowed-origins — http://localhost:3000 in dev
     * (application.properties), an explicit required env var in prod
     * (application-prod.properties has no fallback, so a missing value
     * fails startup loudly rather than silently allowing nothing/everything).
     * Comma-separated if more than one origin is ever needed.
     */
    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // Login is the one endpoint reachable without a token.
                        .requestMatchers("/api/v1/auth/login").permitAll()
                        // API docs stay open in dev; springdoc.swagger-ui.enabled=false
                        // and springdoc.api-docs.enabled=false in application-prod.properties
                        // already turn these off entirely in production.
                        // /v3/api-docs/** is included defensively alongside our custom
                        // /api-docs path — some springdoc versions fetch an internal
                        // swagger-config resource at the hardcoded default path regardless
                        // of springdoc.api-docs.path, which would otherwise 401 silently.
                        .requestMatchers("/api-docs/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Applies to every route ("/**") rather than just /api/v1/** — Swagger UI's
     * own JS also needs to be fetchable cross-origin if it's ever embedded
     * elsewhere, and there's no other cross-origin surface in this app to
     * scope more narrowly than that anyway.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // No cookies/session in play (stateless JWT via Authorization header),
        // so credentials aren't needed — kept false rather than true-by-default,
        // since true would also force an exact-origin allow-list anyway (no "*").
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Registered for completeness / future use (e.g. an alternate login path
     * that wants Spring Security's own authenticate() flow). AuthServiceImpl
     * currently verifies passwords directly via PasswordEncoder rather than
     * routing through this manager — both are valid; this keeps the standard
     * Spring Security piece available without forcing that refactor now.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }
}

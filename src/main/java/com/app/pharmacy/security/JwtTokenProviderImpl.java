package com.app.pharmacy.security;

import com.app.pharmacy.domain.entity.Staff;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * HS256-signed JWTs. The subject claim is Staff.id (not email) so the
 * filter can load the current user by primary key without a second
 * lookup-by-string-parsing step; email and role are carried as claims
 * for convenience/logging, not as the source of truth for authorization
 * (that's re-checked against the DB via CustomUserDetailsService on every request).
 */
@Slf4j
@Component
public class JwtTokenProviderImpl implements JwtTokenProvider {

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtTokenProviderImpl(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        // HS256 requires a key of at least 256 bits (32 bytes) — application-prod.properties
        // has no fallback for app.jwt.secret specifically so a short/placeholder value
        // can't accidentally reach production.
        this.signingKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String generateToken(Staff staff) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getExpirationMs());

        return Jwts.builder()
                .subject(staff.getId().toString())
                .claim("email", staff.getEmail())
                .claim("role", staff.getRole().name())
                .claim("fullName", staff.getFullName())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    @Override
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public UUID getStaffIdFromToken(String token) {
        Claims claims = Jwts.parser().verifyWith(signingKey).build()
                .parseSignedClaims(token)
                .getPayload();
        return UUID.fromString(claims.getSubject());
    }
}

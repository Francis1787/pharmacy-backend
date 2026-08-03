package com.app.pharmacy.security;

import com.app.pharmacy.domain.entity.Staff;

import java.util.UUID;

/**
 * Contract for issuing and validating access tokens. Implemented by
 * JwtTokenProviderImpl using the jjwt library (HS256, HMAC-signed).
 */
public interface JwtTokenProvider {

    /** Issues a signed token after a successful login. Subject = Staff.id. */
    String generateToken(Staff staff);

    /** True if the token's signature and expiry are both valid. */
    boolean validateToken(String token);

    /** Extracts the Staff.id (subject claim) from a token already known to be valid. */
    UUID getStaffIdFromToken(String token);
}

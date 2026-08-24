package com.app.pharmacy.security;

import com.app.pharmacy.domain.entity.Staff;
import com.app.pharmacy.support.TestFixtures;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Token issuance and validation.
 *
 * The three properties that actually matter for security are covered here:
 * a token signed with a different key must not validate, a tampered token
 * must not validate, and an expired token must not validate. Everything
 * else in the filter chain trusts this class's yes/no answer.
 */
@DisplayName("JwtTokenProvider — issuance and validation")
class JwtTokenProviderImplTest {

    private static final String SECRET = "test-secret-key-that-is-at-least-32-bytes-long-for-hs256";

    private JwtProperties properties;
    private JwtTokenProviderImpl provider;
    private Staff staff;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setExpirationMs(3_600_000L);
        provider = new JwtTokenProviderImpl(properties);
        staff = TestFixtures.pharmacist();
    }

    @Nested
    @DisplayName("Issuance")
    class Issuance {

        @Test
        @DisplayName("uses Staff.id as the subject so the filter can load by primary key")
        void subjectIsStaffId() {
            String token = provider.generateToken(staff);

            assertThat(provider.getStaffIdFromToken(token)).isEqualTo(staff.getId());
        }

        @Test
        @DisplayName("carries email, role and fullName as claims")
        void carriesProfileClaims() {
            Claims claims = parse(provider.generateToken(staff));

            assertThat(claims.get("email", String.class)).isEqualTo(staff.getEmail());
            assertThat(claims.get("role", String.class)).isEqualTo(staff.getRole().name());
            assertThat(claims.get("fullName", String.class)).isEqualTo(staff.getFullName());
        }

        @Test
        @DisplayName("never puts the password hash in the token")
        void omitsPasswordHash() {
            String token = provider.generateToken(staff);

            assertThat(parse(token)).doesNotContainKey("passwordHash");
            assertThat(token).doesNotContain(staff.getPasswordHash());
        }

        @Test
        @DisplayName("sets expiry to issuedAt + the configured expiration-ms")
        void honoursConfiguredExpiry() {
            Claims claims = parse(provider.generateToken(staff));

            long lifetimeMs = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
            assertThat(lifetimeMs).isEqualTo(properties.getExpirationMs());
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("accepts a token it just issued")
        void acceptsOwnToken() {
            assertThat(provider.validateToken(provider.generateToken(staff))).isTrue();
        }

        @Test
        @DisplayName("rejects a token signed with a different secret")
        void rejectsForeignSignature() {
            JwtProperties other = new JwtProperties();
            other.setSecret("a-completely-different-secret-key-32-bytes-plus");
            other.setExpirationMs(3_600_000L);
            String foreignToken = new JwtTokenProviderImpl(other).generateToken(staff);

            assertThat(provider.validateToken(foreignToken)).isFalse();
        }

        @Test
        @DisplayName("rejects a token whose payload has been tampered with")
        void rejectsTamperedPayload() {
            String token = provider.generateToken(staff);
            String[] parts = token.split("\\.");
            String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                    "{\"sub\":\"00000000-0000-0000-0000-000000000000\",\"role\":\"Admin\"}"
                            .getBytes(StandardCharsets.UTF_8));

            assertThat(provider.validateToken(parts[0] + "." + forgedPayload + "." + parts[2])).isFalse();
        }

        @Test
        @DisplayName("rejects an already-expired token")
        void rejectsExpiredToken() {
            JwtProperties expiring = new JwtProperties();
            expiring.setSecret(SECRET);
            expiring.setExpirationMs(-1_000L); // issued already expired
            String expired = new JwtTokenProviderImpl(expiring).generateToken(staff);

            assertThat(provider.validateToken(expired)).isFalse();
        }

        @Test
        @DisplayName("rejects structurally invalid input instead of throwing")
        void rejectsGarbage() {
            assertThat(provider.validateToken("not-a-jwt")).isFalse();
            assertThat(provider.validateToken("")).isFalse();
            assertThat(provider.validateToken("a.b.c")).isFalse();
        }

        @Test
        @DisplayName("getStaffIdFromToken refuses an unverifiable token rather than trusting its claims")
        void staffIdExtractionVerifiesSignature() {
            JwtProperties other = new JwtProperties();
            other.setSecret("a-completely-different-secret-key-32-bytes-plus");
            other.setExpirationMs(3_600_000L);
            String foreignToken = new JwtTokenProviderImpl(other).generateToken(staff);

            assertThatThrownBy(() -> provider.getStaffIdFromToken(foreignToken))
                    .isInstanceOf(io.jsonwebtoken.JwtException.class);
        }
    }

    @Nested
    @DisplayName("Key strength")
    class KeyStrength {

        @Test
        @DisplayName("refuses to start with a secret shorter than the 256 bits HS256 requires")
        void rejectsShortSecret() {
            JwtProperties weak = new JwtProperties();
            weak.setSecret("too-short");
            weak.setExpirationMs(3_600_000L);

            assertThatThrownBy(() -> new JwtTokenProviderImpl(weak))
                    .as("a weak JWT_SECRET must fail fast at startup, not sign tokens")
                    .isInstanceOf(io.jsonwebtoken.security.WeakKeyException.class);
        }
    }

    private Claims parse(String token) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}

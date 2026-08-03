package com.app.pharmacy.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds app.jwt.secret and app.jwt.expiration-ms from application*.properties.
 * secret has no fallback in application-prod.properties by design — startup
 * fails loudly there if JWT_SECRET isn't set, rather than signing tokens with
 * a predictable dev placeholder.
 */
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private String secret;
    private long expirationMs;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }
}

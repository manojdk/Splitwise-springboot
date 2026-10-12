package com.splitwise.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * JWT settings bound from {@code app.jwt.*}. The secret comes from the {@code JWT_SECRET} environment
 * variable and must be at least 32 bytes (256 bits, required for HS256); the application refuses to
 * start otherwise.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("PT1H") Duration expiration,
        @DefaultValue("splitwise-api") String issuer) {

    public static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.jwt.secret (env JWT_SECRET) must be set");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret (env JWT_SECRET) must be at least " + MIN_SECRET_BYTES + " bytes long");
        }
        if (expiration == null || expiration.isNegative() || expiration.isZero()) {
            throw new IllegalStateException("app.jwt.expiration must be a positive duration");
        }
    }

    public byte[] secretBytes() {
        return secret.getBytes(StandardCharsets.UTF_8);
    }
}

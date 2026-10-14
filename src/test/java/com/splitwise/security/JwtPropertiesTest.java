package com.splitwise.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtPropertiesTest {

    @Test
    void acceptsSecretOfAtLeast32Bytes() {
        JwtProperties props = new JwtProperties("x".repeat(32), Duration.ofMinutes(5), "issuer", Duration.ofDays(7));
        assertThat(props.secretBytes()).hasSize(32);
    }

    @Test
    void failsFastOnShortSecret() {
        assertThatThrownBy(() -> new JwtProperties("too-short", Duration.ofHours(1), "issuer", Duration.ofDays(7)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void failsFastOnMissingSecret() {
        assertThatThrownBy(() -> new JwtProperties(null, Duration.ofHours(1), "issuer", Duration.ofDays(7)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be set");
    }
}

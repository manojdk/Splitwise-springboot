package com.splitwise.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.splitwise.security.JwtProperties;
import com.splitwise.security.TokenService;
import com.splitwise.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * An active user's token is renewed in the same session, so they are not signed out every hour, but no session
 * outlives app.jwt.max-session.
 */
class TokenRefreshIntegrationTest extends ApiTestSupport {

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtProperties properties;

    @Test
    void aValidTokenIsExchangedForAFreshOneThatWorks() throws Exception {
        Session alice = user("Alice");

        JsonNode fresh = read(call(alice, post("/api/auth/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(properties.expiration().toSeconds())));

        String token = fresh.get("accessToken").asText();
        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alice.id()));
    }

    @Test
    void aSessionCannotBeRefreshedPastItsMaximumAge() throws Exception {
        Session alice = user("Alice");
        String old = token(alice.id(), Instant.now().minus(properties.maxSession()).minus(Duration.ofMinutes(1)));

        // 403, not 401: the token still works until it expires, so the app must not sign the user out at once
        mvc.perform(post("/api/auth/refresh").header(HttpHeaders.AUTHORIZATION, "Bearer " + old))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Your session has ended; please sign in again"));
    }

    @Test
    void aRefreshKeepsTheSessionStartSoTheMaximumCannotBeDodged() throws Exception {
        Session alice = user("Alice");
        Instant signedIn = Instant.now().minus(Duration.ofDays(3));
        String old = token(alice.id(), signedIn);

        String renewed = read(mvc.perform(post("/api/auth/refresh").header(HttpHeaders.AUTHORIZATION, "Bearer " + old))
                .andExpect(status().isOk())).get("accessToken").asText();

        // The payload of the new token still says the session began three days ago
        String payload = new String(java.util.Base64.getUrlDecoder().decode(renewed.split("\\.")[1]));
        assertThat(json.readTree(payload).get(TokenService.SESSION_START).asLong()).isEqualTo(signedIn.getEpochSecond());
    }

    @Test
    void refreshingNeedsAToken() throws Exception {
        mvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
    }

    /** A still-valid token whose session began at {@code sessionStart}, signed like the app's own. */
    private String token(long userId, Instant sessionStart) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(10)))
                .claim(TokenService.SESSION_START, sessionStart.getEpochSecond())
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}

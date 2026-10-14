package com.splitwise.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.splitwise.security.JwtProperties;
import com.splitwise.security.TokenService;
import com.splitwise.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The user's own account: name, password, and ending sessions early (a token can be revoked). */
class AccountIntegrationTest extends ApiTestSupport {

    private static final String SIGNED_OUT = "You were signed out of this device; please sign in again";

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private JwtProperties properties;

    @Test
    void theNameCanBeChangedAndIsTrimmed() throws Exception {
        Session alice = user("Alice");

        call(alice, patchJson("/api/users/me", Map.of("name", "  Alice Smith ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice Smith"))
                .andExpect(jsonPath("$.email").value(alice.email()));
        call(alice, get("/api/users/me")).andExpect(jsonPath("$.name").value("Alice Smith"));

        call(alice, patchJson("/api/users/me", Map.of("name", "   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("name: must not be blank"));
    }

    @Test
    void changingThePasswordSignsOutOtherSessionsButKeepsThisOne() throws Exception {
        Session phone = user("Alice");
        Session laptop = signIn(phone.email(), "password123");

        String fresh = read(call(phone, postJson("/api/users/me/password",
                Map.of("currentPassword", "password123", "newPassword", "a-new-password")))
                .andExpect(status().isOk())).get("accessToken").asText();

        // The other device, and the token that made the change, are signed out at once
        call(laptop, get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(SIGNED_OUT));
        call(phone, get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + fresh))
                .andExpect(status().isOk());

        // Only the new password signs in now
        mvc.perform(postJson("/api/auth/login", Map.of("email", phone.email(), "password", "password123")))
                .andExpect(status().isUnauthorized());
        signIn(phone.email(), "a-new-password");
    }

    @Test
    void theCurrentPasswordMustBeRight() throws Exception {
        Session alice = user("Alice");

        call(alice, postJson("/api/users/me/password",
                Map.of("currentPassword", "wrong-password", "newPassword", "a-new-password")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));
        // Nothing changed: the session still works, and so does the old password
        call(alice, get("/api/users/me")).andExpect(status().isOk());
        signIn(alice.email(), "password123");
    }

    @Test
    void guessingTheCurrentPasswordIsThrottledLikeSignIn() throws Exception {
        Session alice = user("Alice");
        for (int i = 0; i < 5; i++) {
            call(alice, postJson("/api/users/me/password",
                    Map.of("currentPassword", "guess-" + i, "newPassword", "a-new-password")))
                    .andExpect(status().isForbidden());
        }

        // Even the right password is refused now, and so is signing in to that account
        call(alice, postJson("/api/users/me/password",
                Map.of("currentPassword", "password123", "newPassword", "a-new-password")))
                .andExpect(status().isTooManyRequests());
        mvc.perform(postJson("/api/auth/login", Map.of("email", alice.email(), "password", "password123")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void theNewPasswordFollowsTheSameRulesAsSignUpAndMustDiffer() throws Exception {
        Session alice = user("Alice");

        call(alice, postJson("/api/users/me/password", Map.of("currentPassword", "password123", "newPassword", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("newPassword: must be at least 8 characters"));
        call(alice, postJson("/api/users/me/password",
                Map.of("currentPassword", "password123", "newPassword", "password123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The new password is the same as the current one"));
    }

    @Test
    void signingOutEverywhereEndsEverySession() throws Exception {
        Session phone = user("Alice");
        Session laptop = signIn(phone.email(), "password123");

        call(laptop, post("/api/auth/logout-all")).andExpect(status().isNoContent());

        call(laptop, get("/api/users/me")).andExpect(status().isUnauthorized());
        call(phone, get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(SIGNED_OUT));
        // A signed-out token cannot be renewed either
        call(phone, post("/api/auth/refresh")).andExpect(status().isUnauthorized());
        // Signing in again starts a session that works
        Session again = signIn(phone.email(), "password123");
        call(again, get("/api/users/me")).andExpect(status().isOk());
    }

    @Test
    void aTokenFromBeforeVersionsExistedStillWorksUntilTheFirstSignOut() throws Exception {
        Session alice = user("Alice");
        Session old = new Session(alice.id(), alice.email(), tokenWithoutVersion(alice.id()));

        call(old, get("/api/users/me")).andExpect(status().isOk());
        call(alice, post("/api/auth/logout-all")).andExpect(status().isNoContent());
        call(old, get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenForAUserThatDoesNotExistIsRefused() throws Exception {
        Session ghost = new Session(Long.MAX_VALUE, "ghost@example.com", tokenWithoutVersion(Long.MAX_VALUE));
        call(ghost, get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    /** bcrypt reads 72 bytes, so the limit is in bytes: 30 rupee signs are 30 characters but 90 bytes. */
    @Test
    void thePasswordLimitIsCountedInBytes() throws Exception {
        String email = "rupee-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String tooLong = "₹".repeat(30);
        String longest = "₹".repeat(24);
        mvc.perform(postJson("/api/auth/register", Map.of("name", "Rupee", "email", email, "password", tooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "password: must be at most 72 bytes (letters with accents and symbols take 2 to 4 each)"));
        mvc.perform(postJson("/api/auth/register", Map.of("name", "Rupee", "email", email, "password", longest)))
                .andExpect(status().isCreated());
        signIn(email, longest);
    }

    @Test
    void eachAccountEndpointNeedsAToken() throws Exception {
        mvc.perform(patchJson("/api/users/me", Map.of("name", "X"))).andExpect(status().isUnauthorized());
        mvc.perform(postJson("/api/users/me/password", Map.of("currentPassword", "x", "newPassword", "y")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout-all")).andExpect(status().isUnauthorized());
    }

    private Session signIn(String email, String password) throws Exception {
        String token = read(mvc.perform(postJson("/api/auth/login", Map.of("email", email, "password", password)))
                .andExpect(status().isOk())).get("accessToken").asText();
        JsonNode me = read(mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()));
        return new Session(me.get("id").asLong(), email, token);
    }

    private MockHttpServletRequestBuilder patchJson(String url, Object body) throws Exception {
        return patch(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    /** A valid token like those issued before token versions existed (no "ver" claim). */
    private String tokenWithoutVersion(long userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(10)))
                .claim(TokenService.SESSION_START, now.getEpochSecond())
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}

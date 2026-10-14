package com.splitwise.security;

import com.splitwise.domain.User;
import com.splitwise.web.dto.TokenResponse;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Issues signed HS256 access tokens whose subject is the user id. Each token carries when its session began
 * ({@code session_start}, the sign-in time), which a refresh copies forward, so a session cannot be kept alive by
 * refreshing for longer than {@code app.jwt.max-session}, and the user's token version, so all of a user's tokens can be
 * revoked at once.
 */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final JwtProperties properties;

    public TokenService(JwtEncoder encoder, JwtProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public static final String SESSION_START = "session_start";
    /** The user's token version when the token was issued; see {@link SessionVersionValidator}. */
    public static final String TOKEN_VERSION = "ver";

    /** A new session, at sign-in. */
    public TokenResponse issue(User user) {
        return issue(user, Instant.now());
    }

    /** A token for a session that began at {@code sessionStart}. */
    public TokenResponse issue(User user, Instant sessionStart) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(properties.expiration()))
                .claim("email", user.getEmail())
                .claim(SESSION_START, sessionStart.getEpochSecond())
                .claim(TOKEN_VERSION, user.getTokenVersion())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", properties.expiration().toSeconds());
    }
}

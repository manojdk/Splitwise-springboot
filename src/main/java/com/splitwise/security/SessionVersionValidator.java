package com.splitwise.security;

import com.splitwise.repository.UserRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

/**
 * Refuses a token whose user no longer exists, or whose token version is older than the user's: the password was
 * changed or the user signed out of all devices since it was issued. A token without a version (issued before
 * versions existed) counts as version 0. Costs one primary-key lookup per authenticated request.
 */
public class SessionVersionValidator implements OAuth2TokenValidator<Jwt> {

    /** Shown to the signed-out client (see JsonSecurityErrorHandler). */
    public static final String SIGNED_OUT = "You were signed out of this device; please sign in again";

    private static final OAuth2Error ERROR = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, SIGNED_OUT, null);

    private final UserRepository users;

    public SessionVersionValidator(UserRepository users) {
        this.users = users;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Optional<Integer> current = userId(token).flatMap(users::findTokenVersion);
        Object claim = token.getClaims().get(TokenService.TOKEN_VERSION);
        int version = claim instanceof Number number ? number.intValue() : 0;
        return current.isPresent() && current.get() == version
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(ERROR);
    }

    private static Optional<Long> userId(Jwt token) {
        try {
            return Optional.of(Long.valueOf(token.getSubject()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}

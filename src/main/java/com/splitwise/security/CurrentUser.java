package com.splitwise.security;

import org.springframework.security.oauth2.jwt.Jwt;

/** Extracts the authenticated user's id from the validated JWT. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long id(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}

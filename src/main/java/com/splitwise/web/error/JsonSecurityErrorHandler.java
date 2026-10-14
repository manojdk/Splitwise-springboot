package com.splitwise.web.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitwise.security.SessionVersionValidator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Writes 401/403 responses raised by the security filter chain in the same JSON shape as the API. */
@Component
public class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JsonSecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // A signed-out session says so, so the app can tell the user why they have to sign in again
        boolean signedOut = authException instanceof OAuth2AuthenticationException oauth
                && oauth.getError().getDescription() != null
                && oauth.getError().getDescription().contains(SessionVersionValidator.SIGNED_OUT);
        write(response, HttpStatus.UNAUTHORIZED,
                signedOut ? SessionVersionValidator.SIGNED_OUT : "Missing or invalid access token", request);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "Access denied", request);
    }

    private void write(HttpServletResponse response, HttpStatus status, String message,
                       HttpServletRequest request) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if (status == HttpStatus.UNAUTHORIZED) {
            response.setHeader("WWW-Authenticate", "Bearer");
        }
        objectMapper.writeValue(response.getOutputStream(),
                ErrorResponse.of(status, message, request.getRequestURI()));
    }
}

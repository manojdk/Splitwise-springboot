package com.splitwise.web.error;

import org.springframework.http.HttpStatus;

import java.time.Instant;

/** Uniform error body returned by every failing endpoint. */
public record ErrorResponse(Instant timestamp, int status, String error, String message, String path) {

    public static ErrorResponse of(HttpStatus status, String message, String path) {
        return new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, path);
    }
}

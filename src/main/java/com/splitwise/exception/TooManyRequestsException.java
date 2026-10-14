package com.splitwise.exception;

import org.springframework.http.HttpStatus;

import java.time.Duration;

/** 429: the client should wait {@code retryAfter} before trying again (sent as the Retry-After header). */
public class TooManyRequestsException extends ApiException {

    private final Duration retryAfter;

    public TooManyRequestsException(String message, Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}

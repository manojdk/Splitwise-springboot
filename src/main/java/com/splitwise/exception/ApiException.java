package com.splitwise.exception;

import org.springframework.http.HttpStatus;

/** Base type for expected business errors; the HTTP status is mapped by the global exception handler. */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

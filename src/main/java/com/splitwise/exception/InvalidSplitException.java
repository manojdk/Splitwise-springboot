package com.splitwise.exception;

/** Thrown when the shares of an expense are inconsistent with its amount or split type. */
public class InvalidSplitException extends BadRequestException {

    public InvalidSplitException(String message) {
        super(message);
    }
}

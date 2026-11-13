package com.propertyapp.exception;

/** The request is valid but the current state forbids it (409). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}

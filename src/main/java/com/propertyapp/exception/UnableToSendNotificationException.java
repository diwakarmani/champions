package com.propertyapp.exception;

public class UnableToSendNotificationException extends RuntimeException {

    public UnableToSendNotificationException(String message) {
        super(message);
    }
}
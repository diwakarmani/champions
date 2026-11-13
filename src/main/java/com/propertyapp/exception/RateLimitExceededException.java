package com.propertyapp.exception;

import lombok.Getter;

/** Answered with 429 and a Retry-After header (ADR-0007). */
@Getter
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super("Too many requests. Please try again later.");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }
}

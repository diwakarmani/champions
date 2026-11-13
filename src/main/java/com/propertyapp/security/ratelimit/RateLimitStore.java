package com.propertyapp.security.ratelimit;

import java.time.Duration;

/**
 * Counts requests per key. In memory for one instance; a Redis-backed store can replace it
 * when the API runs on more than one instance (ADR-0007).
 */
public interface RateLimitStore {

    /**
     * Takes one request from the key's allowance of {@code capacity} per {@code window}.
     *
     * @return 0 if allowed, otherwise the seconds until the next request would be allowed
     */
    long tryConsume(String key, int capacity, Duration window);
}

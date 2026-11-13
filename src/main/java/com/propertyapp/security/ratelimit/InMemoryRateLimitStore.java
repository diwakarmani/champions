package com.propertyapp.security.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Bucket4j buckets held in a bounded Caffeine cache, so idle keys are forgotten. */
@Component
public class InMemoryRateLimitStore implements RateLimitStore {

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(25))
            .maximumSize(200_000)
            .build();

    @Override
    public long tryConsume(String key, int capacity, Duration window) {
        // The limit is part of the key, so changing a setting never reuses a bucket built for another limit.
        Bucket bucket = buckets.get(key + "|" + capacity + "|" + window.toSeconds(), ignored -> Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(capacity).refillIntervally(capacity, window).build())
                .build());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return 0;
        }
        // Round up, never past the window: truncating and adding 1 gave 3601 s for an exact hour (S13).
        long nanos = probe.getNanosToWaitForRefill();
        return Math.max(1, (nanos + TimeUnit.SECONDS.toNanos(1) - 1) / TimeUnit.SECONDS.toNanos(1));
    }
}

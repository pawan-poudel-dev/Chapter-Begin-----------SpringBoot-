package com.clubbouncer;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A bucket holds up to `capacity` tokens and refills at a steady rate.
 * Each request takes one token. This allows a short burst up to the capacity,
 * then holds callers to the steady refill rate. It stores only two numbers per guest.
 */
class TokenBucketLimiter implements RateLimiter {
    private static final class Bucket {
        double tokens;
        long last;
        Bucket(double tokens, long last) { this.tokens = tokens; this.last = last; }
    }

    private final int capacity;
    private final double refillPerSecond;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    TokenBucketLimiter(int capacity, double refillPerSecond, Clock clock) {
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.clock = clock;
    }

    @Override
    public Decision tryAcquire(String key) {
        long now = clock.millis();
        Bucket b = buckets.computeIfAbsent(key, k -> new Bucket(capacity, now));
        synchronized (b) {
            refill(b, now);
            if (b.tokens >= 1) {
                b.tokens -= 1;
                return new Decision(true, (int) Math.floor(b.tokens), 0);
            }
            long wait = (long) Math.ceil((1 - b.tokens) / refillPerSecond * 1000);
            return new Decision(false, 0, wait);
        }
    }

    @Override
    public int remaining(String key) {
        Bucket b = buckets.get(key);
        if (b == null) return capacity;
        synchronized (b) {
            refill(b, clock.millis());
            return (int) Math.floor(b.tokens);
        }
    }

    private void refill(Bucket b, long now) {
        long elapsed = Math.max(0, now - b.last);
        b.tokens = Math.min(capacity, b.tokens + elapsed / 1000.0 * refillPerSecond);
        b.last = now;
    }
}

package com.clubbouncer;

import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BouncerService {

    private final Map<Algorithm, RateLimiter> limiters = new EnumMap<>(Algorithm.class);
    private final int limit;
    private final int windowSeconds;

    public BouncerService(Clock clock,
                          @Value("${bouncer.limit:5}") int limit,
                          @Value("${bouncer.window-seconds:10}") int windowSeconds) {
        if (limit < 1 || windowSeconds < 2)
            throw new IllegalArgumentException("bouncer.limit must be >= 1 and bouncer.window-seconds >= 2");
        this.limit = limit;
        this.windowSeconds = windowSeconds;
        long windowMs = windowSeconds * 1000L;
        limiters.put(Algorithm.FIXED_WINDOW, new FixedWindowLimiter(limit, windowMs, clock));
        limiters.put(Algorithm.SLIDING_WINDOW, new SlidingWindowLimiter(limit, windowMs, clock));
        // Same average rate as the windows: `limit` tokens every `windowSeconds`.
        limiters.put(Algorithm.TOKEN_BUCKET, new TokenBucketLimiter(limit, limit / (double) windowSeconds, clock));
    }

    public Decision enter(Algorithm algorithm, String guest) { return limiters.get(algorithm).tryAcquire(guest); }

    public int remaining(Algorithm algorithm, String guest) { return limiters.get(algorithm).remaining(guest); }

    public int limit() { return limit; }

    public int windowSeconds() { return windowSeconds; }
}

package com.clubbouncer;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simplest idea: count requests inside a block of time, then reset the count.
 * Flaw: a burst at the end of one block plus a burst at the start of the next
 * lets nearly double the limit through in a very short moment.
 */
class FixedWindowLimiter implements RateLimiter {
    private static final class Window { long start = -1; int count; }

    private final int limit;
    private final long windowMs;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    FixedWindowLimiter(int limit, long windowMs, Clock clock) {
        this.limit = limit;
        this.windowMs = windowMs;
        this.clock = clock;
    }

    @Override
    public Decision tryAcquire(String key) {
        long now = clock.millis();
        Window w = windows.computeIfAbsent(key, k -> new Window());
        synchronized (w) {
            if (w.start < 0 || now - w.start >= windowMs) { w.start = now; w.count = 0; }
            if (w.count < limit) {
                w.count++;
                return new Decision(true, limit - w.count, 0);
            }
            return new Decision(false, 0, w.start + windowMs - now);
        }
    }

    @Override
    public int remaining(String key) {
        Window w = windows.get(key);
        if (w == null) return limit;
        long now = clock.millis();
        synchronized (w) {
            if (w.start < 0 || now - w.start >= windowMs) return limit;
            return limit - w.count;
        }
    }
}

package com.clubbouncer;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers the exact time of every recent request and only counts the ones
 * inside the last window. Precise, with no boundary trick. The cost is memory:
 * it stores up to `limit` timestamps per guest.
 */
class SlidingWindowLimiter implements RateLimiter {
    private final int limit;
    private final long windowMs;
    private final Clock clock;
    private final Map<String, Deque<Long>> logs = new ConcurrentHashMap<>();

    SlidingWindowLimiter(int limit, long windowMs, Clock clock) {
        this.limit = limit;
        this.windowMs = windowMs;
        this.clock = clock;
    }

    @Override
    public Decision tryAcquire(String key) {
        long now = clock.millis();
        Deque<Long> log = logs.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (log) {
            evict(log, now);
            if (log.size() < limit) {
                log.addLast(now);
                return new Decision(true, limit - log.size(), 0);
            }
            return new Decision(false, 0, log.peekFirst() + windowMs - now);
        }
    }

    @Override
    public int remaining(String key) {
        Deque<Long> log = logs.get(key);
        if (log == null) return limit;
        synchronized (log) {
            evict(log, clock.millis());
            return limit - log.size();
        }
    }

    private void evict(Deque<Long> log, long now) {
        while (!log.isEmpty() && now - log.peekFirst() >= windowMs) log.pollFirst();
    }
}

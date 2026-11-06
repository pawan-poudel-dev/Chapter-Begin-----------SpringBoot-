package com.clubbouncer;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private final TestClock clock = new TestClock();

    /** One request to open the window, four near its end, then n right after the edge. Returns allowed in the last two bursts. */
    private int boundaryAttack(RateLimiter limiter, int attemptsAfterEdge) {
        limiter.tryAcquire("a");                    // t = 0
        clock.set(9_000);
        int allowed = 0;
        for (int i = 0; i < 4; i++) if (limiter.tryAcquire("a").allowed()) allowed++;
        clock.set(10_000);
        for (int i = 0; i < attemptsAfterEdge; i++) if (limiter.tryAcquire("a").allowed()) allowed++;
        return allowed;
    }

    @Test
    void fixedWindowLetsAlmostDoubleThroughAtTheBoundary() {
        // limit is 5 per 10 s, yet 9 requests get in within one second
        assertEquals(9, boundaryAttack(new FixedWindowLimiter(5, 10_000, clock), 5));
    }

    @Test
    void slidingWindowHoldsTheLimitAtTheBoundary() {
        assertEquals(5, boundaryAttack(new SlidingWindowLimiter(5, 10_000, clock), 5));
    }

    @Test
    void tokenBucketAllowsABurstThenTheSteadyRate() {
        TokenBucketLimiter limiter = new TokenBucketLimiter(5, 1.0, clock);
        for (int i = 0; i < 5; i++) assertTrue(limiter.tryAcquire("a").allowed());
        Decision blocked = limiter.tryAcquire("a");
        assertFalse(blocked.allowed());
        assertEquals(1_000, blocked.retryAfterMillis());
        clock.set(1_000);
        assertTrue(limiter.tryAcquire("a").allowed());
        assertFalse(limiter.tryAcquire("a").allowed());
    }

    @Test
    void tokenBucketNeverHoldsMoreThanItsCapacity() {
        TokenBucketLimiter limiter = new TokenBucketLimiter(5, 1.0, clock);
        limiter.tryAcquire("a");
        clock.set(3_600_000); // an hour idle
        assertEquals(5, limiter.remaining("a"));
    }

    @Test
    void blockedGuestsAreToldHowLongToWait() {
        FixedWindowLimiter limiter = new FixedWindowLimiter(2, 10_000, clock);
        limiter.tryAcquire("a");
        limiter.tryAcquire("a");
        clock.set(4_000);
        Decision d = limiter.tryAcquire("a");
        assertFalse(d.allowed());
        assertEquals(6_000, d.retryAfterMillis());
    }

    @Test
    void remainingDoesNotCountAsARequest() {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(3, 10_000, clock);
        for (int i = 0; i < 20; i++) limiter.remaining("a");
        assertEquals(3, limiter.remaining("a"));
        assertTrue(limiter.tryAcquire("a").allowed());
        assertEquals(2, limiter.remaining("a"));
    }

    @Test
    void guestsAreLimitedIndependently() {
        for (RateLimiter limiter : List.of(new FixedWindowLimiter(1, 10_000, clock),
                                           new SlidingWindowLimiter(1, 10_000, clock),
                                           new TokenBucketLimiter(1, 0.1, clock))) {
            assertTrue(limiter.tryAcquire("alice").allowed());
            assertFalse(limiter.tryAcquire("alice").allowed());
            assertTrue(limiter.tryAcquire("bob").allowed());
        }
    }

    @Test
    void neverLetsMoreThanTheLimitThroughUnderConcurrency() throws Exception {
        for (RateLimiter limiter : List.of(new FixedWindowLimiter(50, 10_000, clock),
                                           new SlidingWindowLimiter(50, 10_000, clock),
                                           new TokenBucketLimiter(50, 5, clock))) {
            ExecutorService pool = Executors.newFixedThreadPool(32);
            AtomicInteger allowed = new AtomicInteger();
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 500; i++) {
                futures.add(pool.submit(() -> { if (limiter.tryAcquire("same").allowed()) allowed.incrementAndGet(); }));
            }
            for (Future<?> f : futures) f.get();
            pool.shutdown();
            assertEquals(50, allowed.get(), limiter.getClass().getSimpleName());
        }
    }
}

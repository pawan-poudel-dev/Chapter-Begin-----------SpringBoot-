package com.aidesktopagent.security;

import java.util.concurrent.Callable;
import java.util.function.Predicate;

/**
 * Bounded retries with backoff for retryable failures (Day 38).
 */
public final class RetryPolicy {

    private final int maxAttempts;
    private final long initialBackoffMs;
    private final Predicate<Exception> retryable;

    public RetryPolicy(int maxAttempts, long initialBackoffMs, Predicate<Exception> retryable) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialBackoffMs = Math.max(0, initialBackoffMs);
        this.retryable = retryable != null ? retryable : e -> false;
    }

    public static RetryPolicy networkDefaults() {
        return new RetryPolicy(3, 200, e -> {
            String m = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            return m.contains("timeout") || m.contains("connection") || m.contains("temporarily");
        });
    }

    public <T> T execute(Callable<T> action) throws Exception {
        Exception last = null;
        long backoff = initialBackoffMs;
        for (int i = 1; i <= maxAttempts; i++) {
            try {
                return action.call();
            } catch (Exception e) {
                last = e;
                if (i == maxAttempts || !retryable.test(e)) {
                    throw e;
                }
                Thread.sleep(backoff);
                backoff = Math.min(backoff * 2, 5_000);
            }
        }
        throw last != null ? last : new IllegalStateException("Retry failed");
    }
}

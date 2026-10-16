package com.clubbouncer;

public interface RateLimiter {
    /** Try to get in. Counts against the limit if allowed. */
    Decision tryAcquire(String key);

    /** How many more guests could get in right now. Does not count as a request. */
    int remaining(String key);
}

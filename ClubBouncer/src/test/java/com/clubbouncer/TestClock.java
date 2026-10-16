package com.clubbouncer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock the test controls by hand, so time-based logic is exact and never flaky. */
class TestClock extends Clock {
    private volatile long now;

    void set(long millis) { now = millis; }

    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return Instant.ofEpochMilli(now); }
    @Override public long millis() { return now; }
}

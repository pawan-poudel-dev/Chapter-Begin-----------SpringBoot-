package com.ticketrush;

import java.util.concurrent.locks.LockSupport;

/** Stands in for a database round trip or payment call. Real systems always have a gap like this. */
final class Latency {
    private Latency() {}
    static void simulate() { LockSupport.parkNanos(1_000_000); } // ~1 ms
}

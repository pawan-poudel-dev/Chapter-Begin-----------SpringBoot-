package com.ticketrush;

/**
 * The classic bug: check, then act, as two separate steps.
 * Even though the field is volatile, many threads can pass the check
 * before any of them decrements. This is a check-then-act race condition.
 */
class NaiveStrategy implements TicketStrategy {
    private volatile int remaining;

    NaiveStrategy(int stock) { this.remaining = stock; }

    @Override
    public boolean tryBuy() {
        if (remaining > 0) {          // 1. check
            Latency.simulate();       //    ...other threads sneak in here...
            remaining--;              // 2. act (not atomic)
            return true;
        }
        return false;
    }
}

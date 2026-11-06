package com.ticketrush;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Correct and fast. Reserving a ticket is ONE atomic step (compare-and-set),
 * the same idea as the SQL: UPDATE tickets SET stock = stock - 1 WHERE stock > 0.
 * The slow work happens after the reservation, in parallel.
 */
class AtomicStrategy implements TicketStrategy {
    private final AtomicInteger remaining;

    AtomicStrategy(int stock) { this.remaining = new AtomicInteger(stock); }

    @Override
    public boolean tryBuy() {
        int current;
        do {
            current = remaining.get();
            if (current <= 0) return false;
        } while (!remaining.compareAndSet(current, current - 1));
        Latency.simulate();
        return true;
    }
}

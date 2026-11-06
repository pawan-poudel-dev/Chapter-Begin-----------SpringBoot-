package com.ticketrush;

/** Correct, but slow: one buyer at a time holds the lock, even during the slow step. */
class SynchronizedStrategy implements TicketStrategy {
    private int remaining;

    SynchronizedStrategy(int stock) { this.remaining = stock; }

    @Override
    public synchronized boolean tryBuy() {
        if (remaining > 0) {
            Latency.simulate();
            remaining--;
            return true;
        }
        return false;
    }
}

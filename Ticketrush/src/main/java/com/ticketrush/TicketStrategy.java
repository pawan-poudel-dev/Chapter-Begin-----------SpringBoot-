package com.ticketrush;

/** One way of selling a limited stock of tickets to many buyers at once. */
public interface TicketStrategy {
    /** @return true if this buyer got a ticket */
    boolean tryBuy();
}

package com.ticketrush;

/**
 * outcomes[i] for bot i: 0 = missed out, 1 = valid ticket, 2 = oversold (a ticket that should not exist).
 */
public record SimulationResult(String strategy, int stock, int bots, int ticketsIssued,
                               int oversold, long millis, int[] outcomes) {}

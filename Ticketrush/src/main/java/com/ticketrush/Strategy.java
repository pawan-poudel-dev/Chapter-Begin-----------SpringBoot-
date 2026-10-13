package com.ticketrush;

import java.util.function.IntFunction;

public enum Strategy {
    NAIVE(NaiveStrategy::new),
    SYNCHRONIZED(SynchronizedStrategy::new),
    ATOMIC(AtomicStrategy::new);

    private final IntFunction<TicketStrategy> factory;

    Strategy(IntFunction<TicketStrategy> factory) { this.factory = factory; }

    TicketStrategy create(int stock) { return factory.apply(stock); }
}

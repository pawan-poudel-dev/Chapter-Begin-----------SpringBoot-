package com.ticketrush;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SimulationServiceTest {

    private final SimulationService service = new SimulationService();

    @Test
    void atomicNeverOversells() {
        for (int i = 0; i < 5; i++) {
            SimulationResult r = service.run(Strategy.ATOMIC, 50, 500);
            assertEquals(50, r.ticketsIssued());
            assertEquals(0, r.oversold());
        }
    }

    @Test
    void synchronizedNeverOversells() {
        for (int i = 0; i < 5; i++) {
            SimulationResult r = service.run(Strategy.SYNCHRONIZED, 50, 500);
            assertEquals(50, r.ticketsIssued());
            assertEquals(0, r.oversold());
        }
    }

    @Test
    void naiveOversells() {
        boolean oversold = false;
        for (int i = 0; i < 5 && !oversold; i++) {
            oversold = service.run(Strategy.NAIVE, 50, 500).oversold() > 0;
        }
        assertTrue(oversold, "the check-then-act bug should oversell under load");
    }

    @Test
    void fewerBotsThanTicketsSellsOnlyWhatWasRequested() {
        SimulationResult r = service.run(Strategy.ATOMIC, 100, 30);
        assertEquals(30, r.ticketsIssued());
        assertEquals(0, r.oversold());
    }
}

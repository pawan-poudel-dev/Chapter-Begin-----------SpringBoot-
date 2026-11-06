package com.aidesktopagent.computer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SafeCalculatorTest {

    @Test
    void basicOps() {
        SafeCalculator c = new SafeCalculator();
        assertEquals(14.0, c.evaluate("2+3*4"), 1e-9);
        assertEquals(20.0, c.evaluate("(2+3)*4"), 1e-9);
        assertEquals(2.5, c.evaluate("5/2"), 1e-9);
    }

    @Test
    void rejectsLetters() {
        assertThrows(IllegalArgumentException.class, () -> new SafeCalculator().evaluate("2+abc"));
    }

    @Test
    void divisionByZero() {
        assertThrows(IllegalArgumentException.class, () -> new SafeCalculator().evaluate("1/0"));
    }
}

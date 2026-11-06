package com.aidesktopagent.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentChainTest {

    @Test
    void splitsAndThen() {
        List<String> parts = Agent.splitChain("Open Chrome and then search for Java");
        assertEquals(2, parts.size());
        assertTrue(parts.get(0).toLowerCase().contains("chrome"));
        assertTrue(parts.get(1).toLowerCase().contains("search"));
    }

    @Test
    void doesNotSplitSimpleAnd() {
        // "Open Chrome and search for X" stays one command (combo rule)
        List<String> parts = Agent.splitChain("Open Chrome and search for Java docs");
        assertEquals(1, parts.size());
    }

    @Test
    void splitsAmp() {
        assertEquals(2, Agent.splitChain("analyze project && list sources").size());
    }
}

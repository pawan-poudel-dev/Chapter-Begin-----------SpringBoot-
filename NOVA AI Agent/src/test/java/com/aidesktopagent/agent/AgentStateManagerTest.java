package com.aidesktopagent.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AgentStateManagerTest {

    private AgentStateManager sm;

    @BeforeEach
    void setUp() {
        sm = new AgentStateManager();
    }

    @Test
    void startsIdle() {
        assertEquals(AgentState.IDLE, sm.getState());
    }

    @Test
    void validPathListeningToSuccess() {
        assertTrue(sm.transitionTo(AgentState.LISTENING));
        assertTrue(sm.transitionTo(AgentState.TRANSCRIBING));
        assertTrue(sm.transitionTo(AgentState.THINKING));
        assertTrue(sm.transitionTo(AgentState.VALIDATING));
        assertTrue(sm.transitionTo(AgentState.EXECUTING));
        assertTrue(sm.transitionTo(AgentState.SUCCESS));
        assertEquals(AgentState.SUCCESS, sm.getState());
    }

    @Test
    void rejectsInvalidJump() {
        assertFalse(sm.transitionTo(AgentState.EXECUTING));
        assertEquals(AgentState.IDLE, sm.getState());
    }

    @Test
    void forceStopFromAnywhere() {
        sm.transitionTo(AgentState.LISTENING);
        sm.transitionTo(AgentState.TRANSCRIBING);
        sm.forceStop();
        assertEquals(AgentState.STOPPED, sm.getState());
        assertTrue(sm.isStopped());
    }

    @Test
    void busyFlags() {
        assertFalse(sm.isBusy());
        sm.transitionTo(AgentState.LISTENING);
        assertFalse(sm.isBusy());
        sm.transitionTo(AgentState.TRANSCRIBING);
        assertTrue(sm.isBusy());
    }

    @Test
    void listenersNotified() {
        List<AgentState> seen = new ArrayList<>();
        sm.addListener(seen::add);
        sm.transitionTo(AgentState.LISTENING);
        assertTrue(seen.contains(AgentState.LISTENING));
    }

    @Test
    void resetFromStopped() {
        sm.forceStop();
        sm.resetToIdle();
        assertEquals(AgentState.IDLE, sm.getState());
    }

    @Test
    void waitingForConfirmationPath() {
        sm.transitionTo(AgentState.LISTENING);
        sm.transitionTo(AgentState.TRANSCRIBING);
        sm.transitionTo(AgentState.THINKING);
        sm.transitionTo(AgentState.VALIDATING);
        assertTrue(sm.transitionTo(AgentState.WAITING_FOR_CONFIRMATION));
        assertTrue(sm.transitionTo(AgentState.EXECUTING));
    }
}

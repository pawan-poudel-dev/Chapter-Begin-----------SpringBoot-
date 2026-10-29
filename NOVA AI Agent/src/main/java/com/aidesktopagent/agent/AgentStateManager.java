package com.aidesktopagent.agent;

import com.aidesktopagent.logging.LoggingService;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Enforces valid agent state transitions and notifies listeners.
 * Prevents concurrent processing while EXECUTING / THINKING / etc.
 */
public final class AgentStateManager {

    private static final Map<AgentState, Set<AgentState>> ALLOWED = new EnumMap<>(AgentState.class);

    static {
        ALLOWED.put(AgentState.IDLE, EnumSet.of(
                AgentState.LISTENING, AgentState.THINKING, AgentState.STOPPED));
        ALLOWED.put(AgentState.LISTENING, EnumSet.of(
                AgentState.TRANSCRIBING, AgentState.IDLE, AgentState.STOPPED, AgentState.ERROR));
        ALLOWED.put(AgentState.TRANSCRIBING, EnumSet.of(
                AgentState.THINKING, AgentState.IDLE, AgentState.ERROR, AgentState.STOPPED));
        ALLOWED.put(AgentState.THINKING, EnumSet.of(
                AgentState.VALIDATING, AgentState.ERROR, AgentState.STOPPED, AgentState.IDLE));
        ALLOWED.put(AgentState.VALIDATING, EnumSet.of(
                AgentState.EXECUTING, AgentState.WAITING_FOR_CONFIRMATION,
                AgentState.ERROR, AgentState.STOPPED, AgentState.IDLE));
        ALLOWED.put(AgentState.WAITING_FOR_CONFIRMATION, EnumSet.of(
                AgentState.EXECUTING, AgentState.IDLE, AgentState.ERROR, AgentState.STOPPED));
        ALLOWED.put(AgentState.EXECUTING, EnumSet.of(
                AgentState.SUCCESS, AgentState.ERROR, AgentState.STOPPED));
        ALLOWED.put(AgentState.SUCCESS, EnumSet.of(
                AgentState.IDLE, AgentState.LISTENING, AgentState.STOPPED));
        ALLOWED.put(AgentState.ERROR, EnumSet.of(
                AgentState.IDLE, AgentState.LISTENING, AgentState.STOPPED));
        ALLOWED.put(AgentState.STOPPED, EnumSet.of(AgentState.IDLE));
    }

    private final Object lock = new Object();
    private AgentState state = AgentState.IDLE;
    private final LoggingService log;
    private final CopyOnWriteArrayList<Consumer<AgentState>> listeners = new CopyOnWriteArrayList<>();

    public AgentStateManager() {
        this(LoggingService.forComponent("AgentState"));
    }

    public AgentStateManager(LoggingService log) {
        this.log = log != null ? log : LoggingService.forComponent("AgentState");
    }

    public AgentState getState() {
        synchronized (lock) {
            return state;
        }
    }

    public boolean isStopped() {
        return getState() == AgentState.STOPPED;
    }

    public boolean isBusy() {
        AgentState s = getState();
        return s == AgentState.TRANSCRIBING
                || s == AgentState.THINKING
                || s == AgentState.VALIDATING
                || s == AgentState.EXECUTING
                || s == AgentState.WAITING_FOR_CONFIRMATION;
    }

    /**
     * Attempt a transition. Returns false if not allowed (state unchanged).
     * STOPPED may always be requested from non-STOPPED states via {@link #forceStop()}.
     */
    public boolean transitionTo(AgentState next) {
        Objects.requireNonNull(next, "next");
        synchronized (lock) {
            if (state == next) {
                return true;
            }
            Set<AgentState> allowed = ALLOWED.getOrDefault(state, EnumSet.noneOf(AgentState.class));
            if (!allowed.contains(next)) {
                log.warn("Invalid transition " + state + " → " + next);
                return false;
            }
            AgentState prev = state;
            state = next;
            log.info("State " + prev + " → " + next);
        }
        notifyListeners(next);
        return true;
    }

    /** Emergency stop — always allowed except when already STOPPED. */
    public void forceStop() {
        AgentState next;
        synchronized (lock) {
            if (state == AgentState.STOPPED) {
                return;
            }
            AgentState prev = state;
            state = AgentState.STOPPED;
            next = AgentState.STOPPED;
            log.warn("Force stop from " + prev);
        }
        notifyListeners(next);
    }

    public void resetToIdle() {
        synchronized (lock) {
            if (state == AgentState.STOPPED) {
                state = AgentState.IDLE;
                log.info("State STOPPED → IDLE");
            } else if (state == AgentState.SUCCESS || state == AgentState.ERROR) {
                state = AgentState.IDLE;
            } else if (state != AgentState.IDLE && state != AgentState.LISTENING) {
                // best-effort recovery
                state = AgentState.IDLE;
            }
        }
        notifyListeners(getState());
    }

    public void addListener(Consumer<AgentState> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(Consumer<AgentState> listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(AgentState s) {
        for (Consumer<AgentState> l : listeners) {
            try {
                l.accept(s);
            } catch (Exception e) {
                log.warn("State listener error: " + e.getMessage());
            }
        }
    }
}

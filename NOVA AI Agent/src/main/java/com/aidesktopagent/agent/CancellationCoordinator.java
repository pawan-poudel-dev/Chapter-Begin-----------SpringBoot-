package com.aidesktopagent.agent;

import com.aidesktopagent.coding.BuildRunner;
import com.aidesktopagent.files.FileSearchService;
import com.aidesktopagent.logging.LoggingService;
import com.aidesktopagent.voice.VoiceCommandProcessor;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Emergency stop and interrupt coordination (Day 18).
 * Blocks new work and cancels cancellable operations.
 */
public final class CancellationCoordinator {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AgentStateManager stateManager;
    private final CopyOnWriteArrayList<Runnable> hooks = new CopyOnWriteArrayList<>();
    private final LoggingService log;

    public CancellationCoordinator(AgentStateManager stateManager) {
        this(stateManager, LoggingService.forComponent("Cancel"));
    }

    public CancellationCoordinator(AgentStateManager stateManager, LoggingService log) {
        this.stateManager = Objects.requireNonNull(stateManager);
        this.log = log != null ? log : LoggingService.forComponent("Cancel");
    }

    public void registerHook(Runnable hook) {
        if (hook != null) {
            hooks.add(hook);
        }
    }

    public void bindBuildRunner(BuildRunner runner) {
        registerHook(runner::requestCancel);
    }

    public void bindFileSearch(FileSearchService search) {
        registerHook(search::requestCancel);
    }

    public void bindVoice(VoiceCommandProcessor processor) {
        registerHook(processor::requestCancel);
    }

    public void emergencyStop() {
        cancelled.set(true);
        stateManager.forceStop();
        for (Runnable h : hooks) {
            try {
                h.run();
            } catch (Exception e) {
                log.warn("Cancel hook failed: " + e.getMessage());
            }
        }
        log.warn("Emergency stop engaged");
    }

    public void clear() {
        cancelled.set(false);
        if (stateManager.isStopped()) {
            stateManager.resetToIdle();
        }
    }

    public boolean isCancelled() {
        return cancelled.get() || stateManager.isStopped();
    }
}

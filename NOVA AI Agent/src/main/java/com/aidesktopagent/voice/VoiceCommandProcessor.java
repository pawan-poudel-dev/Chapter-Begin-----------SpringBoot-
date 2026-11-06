package com.aidesktopagent.voice;

import com.aidesktopagent.actions.Action;
import com.aidesktopagent.actions.ActionResult;
import com.aidesktopagent.agent.Agent;
import com.aidesktopagent.agent.AgentException;
import com.aidesktopagent.agent.AgentState;
import com.aidesktopagent.agent.AgentStateManager;
import com.aidesktopagent.logging.LoggingService;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Connects transcription → Agent (plan/validate/execute) → TTS feedback.
 * Never executes raw speech or AI text as OS commands.
 */
public final class VoiceCommandProcessor {

    private final Agent agent;
    private final AgentStateManager stateManager;
    private final TextToSpeech tts;
    private final LoggingService log;
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private final AtomicReference<String> lastCommand = new AtomicReference<>("");
    private final AtomicReference<Long> lastCommandAt = new AtomicReference<>(0L);
    private final long duplicateWindowMs;

    public VoiceCommandProcessor(Agent agent,
                                 AgentStateManager stateManager,
                                 TextToSpeech tts,
                                 Function<Action, Boolean> confirmationHandler) {
        this(agent, stateManager, tts, LoggingService.forComponent("VoiceProcessor"), 2_500L);
    }

    public VoiceCommandProcessor(Agent agent,
                                 AgentStateManager stateManager,
                                 TextToSpeech tts,
                                 LoggingService log,
                                 long duplicateWindowMs) {
        this.agent = Objects.requireNonNull(agent);
        this.stateManager = Objects.requireNonNull(stateManager);
        this.tts = Objects.requireNonNull(tts);
        this.log = log != null ? log : LoggingService.forComponent("VoiceProcessor");
        this.duplicateWindowMs = duplicateWindowMs > 0 ? duplicateWindowMs : 2_500L;
    }

    public void requestCancel() {
        cancelRequested.set(true);
        stateManager.forceStop();
        speakSafe("Stopping.");
    }

    public void clearCancel() {
        cancelRequested.set(false);
        if (stateManager.isStopped()) {
            stateManager.resetToIdle();
        }
    }

    public boolean isCancelRequested() {
        return cancelRequested.get() || stateManager.isStopped();
    }

    /**
     * Process a transcribed command through the full validated pipeline.
     * Agent already validates actions; speech never bypasses validation.
     */
    public List<ActionResult> processTranscription(String transcription) {
        cancelRequested.set(false);

        if (transcription == null || transcription.isBlank()) {
            stateManager.transitionTo(AgentState.ERROR);
            speakSafe("Sorry, I didn't catch that. Could you say it again?");
            stateManager.transitionTo(AgentState.IDLE);
            return List.of();
        }

        String text = transcription.trim();
        if (isEmergencyStop(text)) {
            requestCancel();
            return List.of();
        }

        if (isDuplicate(text)) {
            log.warn("Duplicate command ignored: " + text);
            speakSafe("I think I just did that. Say something new, or repeat clearly.");
            return List.of();
        }

        if (stateManager.isBusy()) {
            log.warn("Busy — ignoring command: " + text);
            speakSafe("One moment — I'm still working on that.");
            return List.of();
        }

        try {
            stateManager.transitionTo(AgentState.THINKING);
            speakSafe(announceIntent(text));

            stateManager.transitionTo(AgentState.VALIDATING);
            // plan() validates; throws if nothing accepted
            agent.plan(text);

            if (isCancelRequested()) {
                speakSafe("Cancelled.");
                return List.of();
            }

            stateManager.transitionTo(AgentState.EXECUTING);
            // handle() re-plans + executes with Agent's confirmation callback
            List<ActionResult> results = agent.handle(text);

            if (isCancelRequested()) {
                stateManager.forceStop();
                return results;
            }

            remember(text);

            boolean anySuccess = results.stream().anyMatch(ActionResult::isSuccess);
            if (anySuccess) {
                stateManager.transitionTo(AgentState.SUCCESS);
                speakSafe(summarizeSuccess(results));
            } else {
                stateManager.transitionTo(AgentState.ERROR);
                speakSafe(summarizeFailure(results));
            }
            stateManager.transitionTo(AgentState.IDLE);
            return results;

        } catch (AgentException e) {
            log.failure("voice-process", e);
            stateManager.transitionTo(AgentState.ERROR);
            speakSafe(friendlyAgentError(e));
            stateManager.transitionTo(AgentState.IDLE);
            return List.of();
        } catch (Exception e) {
            log.failure("voice-process", e);
            stateManager.transitionTo(AgentState.ERROR);
            speakSafe("An unexpected error occurred.");
            stateManager.transitionTo(AgentState.IDLE);
            return List.of();
        }
    }

    static boolean isEmergencyStop(String text) {
        String t = text.trim().toLowerCase(Locale.ROOT).replaceAll("[.!?]$", "");
        return t.equals("stop")
                || t.equals("stop agent")
                || t.equals("cancel")
                || t.equals("abort")
                || t.equals("emergency stop")
                || t.equals("quit agent");
    }

    private boolean isDuplicate(String text) {
        String prev = lastCommand.get();
        long at = lastCommandAt.get();
        return prev != null
                && prev.equalsIgnoreCase(text.trim())
                && (System.currentTimeMillis() - at) < duplicateWindowMs;
    }

    private void remember(String text) {
        lastCommand.set(text.trim());
        lastCommandAt.set(System.currentTimeMillis());
    }

    private String announceIntent(String text) {
        String t = text == null ? "" : text.trim();
        if (t.length() > 80) {
            t = t.substring(0, 77) + "...";
        }
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.startsWith("open ") || lower.startsWith("play ") || lower.startsWith("create ")
                || lower.startsWith("search ") || lower.startsWith("write ")) {
            return "Okay, working on: " + t;
        }
        if (lower.startsWith("hi") || lower.startsWith("hello") || lower.contains("how are you")) {
            return "One moment.";
        }
        return "Okay, " + t;
    }

    private String summarizeSuccess(List<ActionResult> results) {
        String detail = results.stream().filter(ActionResult::isSuccess).findFirst()
                .map(ActionResult::getMessage)
                .orElse("All set.");
        // Chatbot-style spoken reply
        if (detail.length() > 180) {
            detail = detail.substring(0, 177) + "...";
        }
        return "Okay! " + detail;
    }

    private String summarizeFailure(List<ActionResult> results) {
        String detail = results.stream().filter(r -> !r.isSuccess()).findFirst()
                .map(ActionResult::getMessage)
                .orElse("Something went wrong.");
        if (detail.length() > 160) {
            detail = detail.substring(0, 157) + "...";
        }
        return "Hmm, I ran into a problem. " + detail;
    }

    
    private String friendlyAgentError(AgentException e) {
        String m = e.getMessage() != null ? e.getMessage().toLowerCase(Locale.ROOT) : "";
        if (m.contains("no valid actions") || m.contains("planning failed")) {
            return "I couldn't turn that into a safe action.";
        }
        return "Something went wrong.";
    }

    private void speakSafe(String text) {
        try {
            tts.speak(text);
        } catch (SpeechSynthesisException e) {
            log.warn("TTS failed: " + e.getMessage());
            System.out.println("[TTS-fallback] " + text);
        }
    }
}

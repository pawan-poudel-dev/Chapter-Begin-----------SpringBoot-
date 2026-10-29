package com.aidesktopagent.agent;

/**
 * Lifecycle states for the voice-enabled agent.
 */
public enum AgentState {
    IDLE,
    LISTENING,
    TRANSCRIBING,
    THINKING,
    VALIDATING,
    EXECUTING,
    WAITING_FOR_CONFIRMATION,
    SUCCESS,
    ERROR,
    STOPPED
}

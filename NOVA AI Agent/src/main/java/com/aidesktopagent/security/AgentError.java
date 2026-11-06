package com.aidesktopagent.security;

/**
 * Separates user-facing messages from diagnostic detail (Day 36).
 */
public final class AgentError {

    private final ErrorCategory category;
    private final String userMessage;
    private final String diagnostic;
    private final String recoveryHint;

    public AgentError(ErrorCategory category, String userMessage, String diagnostic, String recoveryHint) {
        this.category = category != null ? category : ErrorCategory.UNKNOWN;
        this.userMessage = userMessage != null ? userMessage : "Something went wrong.";
        this.diagnostic = diagnostic;
        this.recoveryHint = recoveryHint;
    }

    public static AgentError of(ErrorCategory cat, String user, String diag, String hint) {
        return new AgentError(cat, user, diag, hint);
    }

    public ErrorCategory getCategory() {
        return category;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public String getDiagnostic() {
        return diagnostic;
    }

    public String getRecoveryHint() {
        return recoveryHint;
    }

    public String toUserFacing() {
        StringBuilder sb = new StringBuilder(userMessage);
        if (recoveryHint != null && !recoveryHint.isBlank()) {
            sb.append(" — ").append(recoveryHint);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return category + ": " + userMessage
                + (diagnostic != null ? " [" + diagnostic + "]" : "");
    }
}

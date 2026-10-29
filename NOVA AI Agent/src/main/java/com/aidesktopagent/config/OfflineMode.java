package com.aidesktopagent.config;

/**
 * Reduced-capability offline mode (Day 58).
 * Never silently switches privacy expectations — callers must surface status.
 */
public final class OfflineMode {

    private final boolean forceOffline;
    private final boolean hasAiKey;
    private final boolean hasSttKey;

    public OfflineMode(boolean forceOffline, boolean hasAiKey, boolean hasSttKey) {
        this.forceOffline = forceOffline;
        this.hasAiKey = hasAiKey;
        this.hasSttKey = hasSttKey;
    }

    public static OfflineMode fromEnv() {
        boolean force = "true".equalsIgnoreCase(System.getenv().getOrDefault("AGENT_OFFLINE", "false"));
        boolean ai = System.getenv("OPENAI_API_KEY") != null && !System.getenv("OPENAI_API_KEY").isBlank();
        return new OfflineMode(force, ai, ai);
    }

    public boolean isOffline() {
        return forceOffline || (!hasAiKey && !hasSttKey);
    }

    public boolean canUseCloudAi() {
        return !forceOffline && hasAiKey;
    }

    public boolean canUseCloudStt() {
        return !forceOffline && hasSttKey;
    }

    public String statusMessage() {
        if (forceOffline) {
            return "Offline mode forced (AGENT_OFFLINE=true). Using rule-based AI and mock/local STT only.";
        }
        if (!hasAiKey) {
            return "No API key — rule-based offline AI. Cloud STT/LLM unavailable.";
        }
        return "Online providers available.";
    }
}

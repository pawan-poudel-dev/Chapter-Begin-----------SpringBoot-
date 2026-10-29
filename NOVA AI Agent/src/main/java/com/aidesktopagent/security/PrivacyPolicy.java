package com.aidesktopagent.security;

/**
 * Documents what leaves the machine (Day 40). Not a network enforcer — guidance + checks.
 */
public final class PrivacyPolicy {

    public String dataSentToProviders() {
        return """
                External data (only when configured):
                - Speech audio → STT provider (e.g. Whisper) when voice mode uses cloud STT
                - Command text → LLM provider when OPENAI_API_KEY (or compatible) is set
                - Spotify Web API requests when SPOTIFY_* tokens are set
                Never sent: passwords, preference secrets (blocked), raw filesystem dumps
                """.trim();
    }

    public String microphoneStatus(boolean listening) {
        return listening
                ? "Microphone: ACTIVE — audio may be sent to the configured STT provider after you finish speaking."
                : "Microphone: idle — not capturing.";
    }

    public boolean isSafeToLog(String value) {
        if (value == null) {
            return true;
        }
        String l = value.toLowerCase();
        return !(l.contains("password") || l.contains("api_key") || l.contains("secret")
                || l.contains("token") || l.startsWith("sk-") || l.contains("bearer "));
    }
}

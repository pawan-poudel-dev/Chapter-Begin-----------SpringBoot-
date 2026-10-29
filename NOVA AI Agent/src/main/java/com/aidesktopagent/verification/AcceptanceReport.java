package com.aidesktopagent.verification;

import com.aidesktopagent.config.OfflineMode;

/**
 * Day 60 demonstration report builder.
 */
public final class AcceptanceReport {

    public String build(FeatureMatrix matrix, OfflineMode offline, int testCount, boolean testsPassed) {
        return """
                ========================================
                AI Desktop Agent — Day 60 Acceptance
                ========================================
                
                Offline status: %s
                
                Automated tests: %s (%d tests)
                
                %s
                Known limitations:
                - Headless environments cannot verify Desktop.browse / GUI apps
                - Spotify playback needs Premium + OAuth + active device
                - Full JavaFX robot animation UI is not shipped (mood layer is ready)
                - Cloud STT/LLM require API keys
                
                Security notes:
                - Actions are typed and validated before execution
                - Destructive actions require explicit YES confirmation
                - Secrets are not stored in preferences or ordinary config files
                - Emergency stop cancels cancellable work
                
                Priorities for future work:
                1. JavaFX companion window wired to AvatarMood
                2. Deeper IDE automation (compile-on-save feedback)
                3. Local STT/LLM packaging for true offline speech
                4. Richer multi-turn coding sessions
                
                ========================================
                """.formatted(
                offline.statusMessage(),
                testsPassed ? "PASSED" : "FAILED OR NOT RUN",
                testCount,
                matrix.render()
        );
    }
}

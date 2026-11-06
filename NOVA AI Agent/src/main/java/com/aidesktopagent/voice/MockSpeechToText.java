package com.aidesktopagent.voice;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Deterministic STT for tests and headless demos.
 * Returns the next string from a supplier (or a fixed phrase).
 */
public final class MockSpeechToText implements SpeechToText {

    private final Supplier<String> script;
    private final boolean failUnclear;

    public MockSpeechToText(String fixedText) {
        this(() -> fixedText, false);
    }

    public MockSpeechToText(Supplier<String> script, boolean failUnclear) {
        this.script = Objects.requireNonNull(script);
        this.failUnclear = failUnclear;
    }

    @Override
    public String providerName() {
        return "mock-stt";
    }

    @Override
    public String transcribe(byte[] audioWavWavOrPcm, Locale language) throws SpeechRecognitionException {
        if (failUnclear) {
            throw new SpeechRecognitionException(
                    SpeechRecognitionException.Kind.UNCLEAR, "Could not understand speech");
        }
        String t = script.get();
        if (t == null || t.isBlank()) {
            throw new SpeechRecognitionException(
                    SpeechRecognitionException.Kind.UNCLEAR, "Empty transcription");
        }
        return t.trim();
    }
}

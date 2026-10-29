package com.aidesktopagent.voice;

/**
 * Speaks text aloud. Independent of speech recognition providers.
 */
public interface TextToSpeech extends AutoCloseable {

    void speak(String text) throws SpeechSynthesisException;

    /** Non-blocking if the provider supports it; default blocks. */
    default void speakAsync(String text) {
        try {
            speak(text);
        } catch (SpeechSynthesisException e) {
            // callers may log
        }
    }

    String providerName();

    @Override
    default void close() {
    }
}

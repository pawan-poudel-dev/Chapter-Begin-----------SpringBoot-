package com.aidesktopagent.voice;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Converts spoken audio into text.
 * Implementations must not execute commands — transcription only.
 */
public interface SpeechToText extends AutoCloseable {

    /**
     * Transcribe a single utterance from the given PCM/WAV audio bytes.
     *
     * @param audioWavWavOrPcm audio data (prefer 16-bit PCM WAV)
     * @param language         BCP-47 language tag hint (e.g. {@code en}); may be null
     * @return recognized text (never null; empty if silence)
     * @throws SpeechRecognitionException on mic/API/timeout failures
     */
    String transcribe(byte[] audioWavWavOrPcm, Locale language) throws SpeechRecognitionException;

    /**
     * Async variant.
     */
    default CompletableFuture<String> transcribeAsync(byte[] audio, Locale language) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return transcribe(audio, language);
            } catch (SpeechRecognitionException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        });
    }

    /** Human-readable provider name. */
    String providerName();

    @Override
    default void close() {
        // optional
    }
}

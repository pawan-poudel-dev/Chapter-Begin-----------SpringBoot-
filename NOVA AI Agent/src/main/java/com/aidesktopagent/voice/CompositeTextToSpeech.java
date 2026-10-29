package com.aidesktopagent.voice;

import com.aidesktopagent.logging.LoggingService;

import java.util.List;

/**
 * Tries providers in order; last resort should be {@link ConsoleTextToSpeech}.
 */
public final class CompositeTextToSpeech implements TextToSpeech {

    private final List<TextToSpeech> providers;
    private final LoggingService log;

    public CompositeTextToSpeech(List<TextToSpeech> providers) {
        this(providers, LoggingService.forComponent("CompositeTTS"));
    }

    public CompositeTextToSpeech(List<TextToSpeech> providers, LoggingService log) {
        this.providers = List.copyOf(providers);
        this.log = log != null ? log : LoggingService.forComponent("CompositeTTS");
    }

    @Override
    public String providerName() {
        return "composite";
    }

    @Override
    public void speak(String text) throws SpeechSynthesisException {
        SpeechSynthesisException last = null;
        for (TextToSpeech p : providers) {
            try {
                p.speak(text);
                return;
            } catch (SpeechSynthesisException e) {
                last = e;
                log.debug("TTS provider " + p.providerName() + " failed: " + e.getMessage());
            }
        }
        if (last != null) {
            throw last;
        }
    }

    @Override
    public void close() {
        for (TextToSpeech p : providers) {
            try {
                p.close();
            } catch (Exception ignored) {
            }
        }
    }
}

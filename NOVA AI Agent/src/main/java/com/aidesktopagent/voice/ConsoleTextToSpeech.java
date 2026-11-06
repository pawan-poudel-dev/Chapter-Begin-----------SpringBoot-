package com.aidesktopagent.voice;

import com.aidesktopagent.logging.LoggingService;

/**
 * Fallback TTS: prints spoken lines to the console (always available).
 */
public final class ConsoleTextToSpeech implements TextToSpeech {

    private final LoggingService log;

    public ConsoleTextToSpeech() {
        this(LoggingService.forComponent("ConsoleTTS"));
    }

    public ConsoleTextToSpeech(LoggingService log) {
        this.log = log != null ? log : LoggingService.forComponent("ConsoleTTS");
    }

    @Override
    public String providerName() {
        return "console";
    }

    @Override
    public void speak(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        String line = "[TTS] " + text;
        System.out.println(line);
        log.info(line);
    }
}

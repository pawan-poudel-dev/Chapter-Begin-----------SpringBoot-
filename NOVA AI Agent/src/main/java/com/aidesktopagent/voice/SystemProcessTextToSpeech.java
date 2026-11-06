package com.aidesktopagent.voice;

import com.aidesktopagent.logging.LoggingService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * TTS via local CLI tools when present: {@code espeak}, {@code espeak-ng}, or macOS {@code say}.
 * Falls back by throwing so callers can use {@link ConsoleTextToSpeech}.
 */
public final class SystemProcessTextToSpeech implements TextToSpeech {

    private final LoggingService log;
    private final String tool;

    public SystemProcessTextToSpeech() {
        this(LoggingService.forComponent("SystemTTS"));
    }

    public SystemProcessTextToSpeech(LoggingService log) {
        this.log = log != null ? log : LoggingService.forComponent("SystemTTS");
        this.tool = detectTool();
    }

    public boolean isAvailable() {
        return tool != null;
    }

    @Override
    public String providerName() {
        return tool != null ? "system:" + tool : "system:unavailable";
    }

    @Override
    public void speak(String text) throws SpeechSynthesisException {
        if (text == null || text.isBlank()) {
            return;
        }
        if (tool == null) {
            throw new SpeechSynthesisException("No system TTS tool (espeak/say) found");
        }
        List<String> cmd = new ArrayList<>();
        if ("say".equals(tool)) {
            cmd.add("say");
            cmd.add(text);
        } else {
            cmd.add(tool);
            cmd.add("-v");
            cmd.add("en");
            cmd.add(text);
        }
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            boolean done = p.waitFor(30, TimeUnit.SECONDS);
            if (!done) {
                p.destroyForcibly();
                throw new SpeechSynthesisException("TTS timed out");
            }
            log.info("Spoke via " + tool + " (" + text.length() + " chars)");
        } catch (IOException e) {
            throw new SpeechSynthesisException("TTS process failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SpeechSynthesisException("TTS interrupted", e);
        }
    }

    private static String detectTool() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            if (commandExists("say")) return "say";
        }
        if (commandExists("espeak-ng")) return "espeak-ng";
        if (commandExists("espeak")) return "espeak";
        return null;
    }

    private static boolean commandExists(String name) {
        try {
            Process p = new ProcessBuilder("which", name).start();
            return p.waitFor(3, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}

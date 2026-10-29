package com.aidesktopagent.voice;

import com.aidesktopagent.logging.LoggingService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Windows built-in voice via System.Speech.Synthesis (PowerShell). Free, no API key.
 */
public final class WindowsTextToSpeech implements TextToSpeech {

    private final LoggingService log;
    private final boolean available;

    public WindowsTextToSpeech() {
        this(LoggingService.forComponent("WindowsTTS"));
    }

    public WindowsTextToSpeech(LoggingService log) {
        this.log = log != null ? log : LoggingService.forComponent("WindowsTTS");
        this.available = isSupported();
    }

    public static boolean isSupported() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public boolean isAvailable() {
        return available;
    }

    @Override
    public String providerName() {
        return available ? "windows-sapi" : "windows-sapi:unavailable";
    }

    @Override
    public void speak(String text) throws SpeechSynthesisException {
        if (text == null || text.isBlank()) {
            return;
        }
        if (!available) {
            throw new SpeechSynthesisException("Windows TTS only on Windows");
        }
        Path script = null;
        try {
            // Escape for PowerShell single-quoted string
            String safe = text.replace("'", "''");
            if (safe.length() > 800) {
                safe = safe.substring(0, 797) + "...";
            }
            script = Files.createTempFile("aether-tts-", ".ps1");
            String ps = """
                    Add-Type -AssemblyName System.Speech
                    $speak = New-Object System.Speech.Synthesis.SpeechSynthesizer
                    $speak.Rate = 1
                    $speak.Speak('%s')
                    $speak.Dispose()
                    """.formatted(safe);
            Files.writeString(script, ps, StandardCharsets.UTF_8);
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                    "-File", script.toAbsolutePath().toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean done = p.waitFor(60, TimeUnit.SECONDS);
            if (!done) {
                p.destroyForcibly();
                throw new SpeechSynthesisException("Windows TTS timed out");
            }
            log.info("Spoke via Windows SAPI (" + text.length() + " chars)");
        } catch (SpeechSynthesisException e) {
            throw e;
        } catch (Exception e) {
            throw new SpeechSynthesisException("Windows TTS failed: " + e.getMessage(), e);
        } finally {
            try {
                if (script != null) Files.deleteIfExists(script);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void close() {
        // nothing to close
    }
}

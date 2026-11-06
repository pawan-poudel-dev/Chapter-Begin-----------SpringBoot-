package com.aidesktopagent.voice;

import com.aidesktopagent.logging.LoggingService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Free on-device speech recognition on Windows via {@code System.Speech}
 * (PowerShell). No cloud API key required.
 *
 * <p>Requires Windows with the .NET System.Speech assembly (standard on
 * desktop Windows). Accuracy depends on the OS speech model and microphone.
 */
public final class WindowsSpeechToText implements SpeechToText {

    private final LoggingService log;

    public WindowsSpeechToText() {
        this(LoggingService.forComponent("WindowsSTT"));
    }

    public WindowsSpeechToText(LoggingService log) {
        this.log = log != null ? log : LoggingService.forComponent("WindowsSTT");
    }

    public static boolean isSupported() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win");
    }

    @Override
    public String providerName() {
        return "windows-system-speech";
    }

    @Override
    public String transcribe(byte[] audioWav, Locale language) throws SpeechRecognitionException {
        if (audioWav == null || audioWav.length < 44) {
            throw new SpeechRecognitionException(
                    SpeechRecognitionException.Kind.UNCLEAR, "Empty or too-short audio");
        }
        if (!isSupported()) {
            throw new SpeechRecognitionException(
                    SpeechRecognitionException.Kind.PROVIDER, "Windows STT only works on Windows");
        }

        Path wav = null;
        Path script = null;
        try {
            wav = Files.createTempFile("aether-stt-", ".wav");
            Files.write(wav, audioWav);
            script = Files.createTempFile("aether-stt-", ".ps1");
            String ps = """
                    Add-Type -AssemblyName System.Speech
                    $engine = New-Object System.Speech.Recognition.SpeechRecognitionEngine
                    try {
                      $engine.SetInputToWaveFile('%s')
                      $engine.LoadGrammar((New-Object System.Speech.Recognition.DictationGrammar))
                      $result = $engine.Recognize()
                      if ($null -eq $result -or [string]::IsNullOrWhiteSpace($result.Text)) {
                        Write-Output ''
                      } else {
                        Write-Output $result.Text
                      }
                    } finally {
                      $engine.Dispose()
                    }
                    """.formatted(wav.toAbsolutePath().toString().replace("'", "''"));
            Files.writeString(script, ps, StandardCharsets.UTF_8);

            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                    "-File", script.toAbsolutePath().toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            boolean done = p.waitFor(45, TimeUnit.SECONDS);
            if (!done) {
                p.destroyForcibly();
                throw new SpeechRecognitionException(
                        SpeechRecognitionException.Kind.TIMEOUT, "Windows STT timed out");
            }
            if (p.exitValue() != 0 && out.isBlank()) {
                throw new SpeechRecognitionException(
                        SpeechRecognitionException.Kind.PROVIDER,
                        "Windows speech failed (exit " + p.exitValue() + "): " + out);
            }
            if (out.isBlank()) {
                throw new SpeechRecognitionException(
                        SpeechRecognitionException.Kind.UNCLEAR,
                        "I couldn't understand that. Please try again more clearly.");
            }
            // Strip PowerShell noise lines if any
            String text = out.lines()
                    .map(String::trim)
                    .filter(l -> !l.isEmpty() && !l.startsWith("Add-Type") && !l.contains("Exception"))
                    .reduce((a, b) -> b)
                    .orElse(out)
                    .trim();
            log.info("Windows STT heard: " + text);
            return text;
        } catch (SpeechRecognitionException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new SpeechRecognitionException(
                    SpeechRecognitionException.Kind.PROVIDER,
                    "Windows STT error: " + e.getMessage(), e);
        } finally {
            try {
                if (wav != null) Files.deleteIfExists(wav);
                if (script != null) Files.deleteIfExists(script);
            } catch (IOException ignored) {
            }
        }
    }
}

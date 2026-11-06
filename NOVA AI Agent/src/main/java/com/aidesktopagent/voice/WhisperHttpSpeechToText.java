package com.aidesktopagent.voice;

import com.aidesktopagent.config.AppConfig;
import com.aidesktopagent.config.ConfigLoader;
import com.aidesktopagent.logging.LoggingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Speech-to-text via OpenAI Whisper HTTP API ({@code /v1/audio/transcriptions}).
 *
 * <p><strong>What is sent:</strong> only the captured utterance WAV bytes for that
 * single recognition request, plus the API key in the Authorization header.
 * Audio is not stored by this client after the HTTP call completes.
 *
 * <p>Requires {@code OPENAI_API_KEY} (or compatible key) in the environment.
 */
public final class WhisperHttpSpeechToText implements SpeechToText {

    private final AppConfig config;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final LoggingService log;
    private final Duration timeout;

    public WhisperHttpSpeechToText(AppConfig config) {
        this(config,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(),
                new ObjectMapper(),
                LoggingService.forComponent("WhisperSTT"),
                Duration.ofSeconds(45));
    }

    public WhisperHttpSpeechToText(AppConfig config, HttpClient http, ObjectMapper mapper,
                                   LoggingService log, Duration timeout) {
        this.config = config;
        this.http = http;
        this.mapper = mapper;
        this.log = log != null ? log : LoggingService.forComponent("WhisperSTT");
        this.timeout = timeout != null ? timeout : Duration.ofSeconds(45);
    }

    @Override
    public String providerName() {
        return "openai-whisper";
    }

    @Override
    public String transcribe(byte[] audioWav, Locale language) throws SpeechRecognitionException {
        if (audioWav == null || audioWav.length == 0) {
            throw new SpeechRecognitionException(
                    SpeechRecognitionException.Kind.UNCLEAR, "Empty audio");
        }
        Optional<String> key = config.resolveApiKey();
        if (key.isEmpty()) {
            key = ConfigLoader.resolveSecret(ConfigLoader.ENV_OPENAI_API_KEY);
        }
        if (key.isEmpty()) {
            throw new SpeechRecognitionException(
                    SpeechRecognitionException.Kind.PROVIDER,
                    "No API key for Whisper (set OPENAI_API_KEY)");
        }

        String baseUrl = config.getAiBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.openai.com/v1";
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        String boundary = "----AgentBoundary" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = buildMultipart(boundary, audioWav, language);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/audio/transcriptions"))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + key.get())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            log.info("Sending utterance (" + audioWav.length + " bytes) to Whisper — not retained locally after response");
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new SpeechRecognitionException(
                        SpeechRecognitionException.Kind.PERMISSION_DENIED,
                        "Whisper API rejected credentials");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new SpeechRecognitionException(
                        SpeechRecognitionException.Kind.NETWORK,
                        "Whisper HTTP " + response.statusCode() + ": "
                                + LoggingService.sanitize(truncate(response.body(), 200)));
            }

            JsonNode root = mapper.readTree(response.body());
            String text = root.path("text").asText("").trim();
            if (text.isEmpty()) {
                throw new SpeechRecognitionException(
                        SpeechRecognitionException.Kind.UNCLEAR, "Could not understand speech");
            }
            log.info("Transcription length=" + text.length());
            return text;
        } catch (SpeechRecognitionException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new SpeechRecognitionException(
                    SpeechRecognitionException.Kind.TIMEOUT, "Speech recognition timed out", e);
        } catch (java.io.IOException e) {
            throw new SpeechRecognitionException(
                    SpeechRecognitionException.Kind.NETWORK, "Network error: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SpeechRecognitionException(
                    SpeechRecognitionException.Kind.CANCELLED, "Interrupted", e);
        } catch (Exception e) {
            throw new SpeechRecognitionException(
                    SpeechRecognitionException.Kind.PROVIDER, e.getMessage(), e);
        }
    }

    private static byte[] buildMultipart(String boundary, byte[] wav, Locale language) {
        String lang = language != null ? language.getLanguage() : "en";
        StringBuilder header = new StringBuilder();
        header.append("--").append(boundary).append("\r\n");
        header.append("Content-Disposition: form-data; name=\"model\"\r\n\r\n");
        header.append("whisper-1\r\n");
        header.append("--").append(boundary).append("\r\n");
        header.append("Content-Disposition: form-data; name=\"language\"\r\n\r\n");
        header.append(lang).append("\r\n");
        header.append("--").append(boundary).append("\r\n");
        header.append("Content-Disposition: form-data; name=\"response_format\"\r\n\r\n");
        header.append("json\r\n");
        header.append("--").append(boundary).append("\r\n");
        header.append("Content-Disposition: form-data; name=\"file\"; filename=\"utterance.wav\"\r\n");
        header.append("Content-Type: audio/wav\r\n\r\n");

        byte[] head = header.toString().getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[head.length + wav.length + tail.length];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(wav, 0, out, head.length, wav.length);
        System.arraycopy(tail, 0, out, head.length + wav.length, tail.length);
        return out;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}

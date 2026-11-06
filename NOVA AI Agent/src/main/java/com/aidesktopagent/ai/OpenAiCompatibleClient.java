package com.aidesktopagent.ai;

import com.aidesktopagent.config.AppConfig;
import com.aidesktopagent.logging.LoggingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.aidesktopagent.security.EndpointValidator;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Calls an OpenAI-compatible Chat Completions API and asks for a structured JSON action plan.
 * API key is read only from the environment via {@link AppConfig#resolveApiKey()}.
 * Never logs the key.
 */
public final class OpenAiCompatibleClient implements AIClient {

    private static final String SYSTEM_PROMPT = """
            You are the planning module of a desktop AI agent.
            Convert the user's command into a JSON action plan.
            Reply with ONLY valid JSON (no markdown, no commentary) of this form:
            {
              "actions": [
                { "type": "<ACTION_TYPE>", "target": "<string>", "parameters": { } }
              ],
              "rationale": "<short reason>"
            }
            Allowed ACTION_TYPE values:
            OPEN_APPLICATION, CLOSE_APPLICATION, OPEN_WEBSITE, SEARCH_WEB, OPEN_FOLDER,
            CREATE_FILE, CREATE_FOLDER, READ_FILE, MOVE_FILE, COPY_FILE, DELETE_FILE,
            TYPE_TEXT, PRESS_KEY,
            SEARCH_YOUTUBE, OPEN_YOUTUBE_VIDEO, PLAY_YOUTUBE,
            OPEN_SPOTIFY, SEARCH_SPOTIFY, PLAY_SPOTIFY,
            PAUSE_MEDIA, RESUME_MEDIA, STOP_MEDIA, NEXT_TRACK, PREVIOUS_TRACK, GET_MEDIA_STATUS,
            ANALYZE_PROJECT, LIST_SOURCE_FILES, READ_SOURCE_FILE, EXPLAIN_ERROR, PROPOSE_CODE_CHANGE, GENERATE_PROJECT, GIT_STATUS,
            CLIPBOARD_READ, CLIPBOARD_WRITE, GET_TIME, SYSTEM_INFO, CALCULATE, SAVE_NOTE, LIST_NOTES, READ_NOTE, SET_TIMER, SHOW_HELP,
            CHAT_REPLY, WRITE_SNIPPET,
            RUN_COMMAND.
            Rules:
            - Prefer a single clear action when possible.
            - Never invent executable code, shell pipelines, or browser scripts.
            - For websites include a full https URL when known; only http/https.
            - YouTube: use PLAY_YOUTUBE or SEARCH_YOUTUBE with a topic; do not claim playback started.
            - Spotify: use PLAY_SPOTIFY / OPEN_SPOTIFY; API playback needs user auth.
            - Pause/resume/next without a target use PAUSE_MEDIA / RESUME_MEDIA / NEXT_TRACK.
            - Coding: ANALYZE_PROJECT / LIST_SOURCE_FILES / READ_SOURCE_FILE / EXPLAIN_ERROR /
              PROPOSE_CODE_CHANGE only analyze or propose — never imply files were written.
            - For files/folders use paths as the user stated.
            - RUN_COMMAND is only for simple allowed utilities; avoid it unless necessary.
            - Greetings/chat: CHAT_REPLY, put the spoken reply text in target.
            - Multiple tasks: multiple objects in actions array.
            - Songs: PLAY_YOUTUBE with song and artist in target.
            - Small code: WRITE_SNIPPET (kind=sum or parameters.code).
            """;

    private final AppConfig config;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final LoggingService log;

    public OpenAiCompatibleClient(AppConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(),
                new ObjectMapper(), LoggingService.forComponent("OpenAiClient"));
    }

    public OpenAiCompatibleClient(AppConfig config, HttpClient http, ObjectMapper mapper, LoggingService log) {
        this.config = config;
        this.http = http;
        this.mapper = mapper;
        this.log = log;
    }

    @Override
    public String planActions(String userCommand) throws AIClientException {
        Optional<String> key = config.resolveApiKey();
        if (key.isEmpty()) {
            throw new AIClientException("No API key configured (set GEMINI_API_KEY / GOOGLE_API_KEY or OPENAI_API_KEY)");
        }

        String baseUrl = config.resolveBaseUrl();
        try {
            EndpointValidator.validateRemoteEndpoint(baseUrl);
        } catch (IllegalArgumentException ex) {
            throw new AIClientException("AI endpoint rejected: " + ex.getMessage());
        }

        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", config.getAiModel());
            body.put("temperature", 0.1);
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", SYSTEM_PROMPT);
            messages.addObject().put("role", "user").put("content", userCommand);

            // JSON mode: OpenAI supports it; Gemini OpenAI-compat usually accepts it too
            ObjectNode responseFormat = body.putObject("response_format");
            responseFormat.put("type", "json_object");

            String jsonBody = mapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + key.get())
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            log.info("Calling AI provider=" + config.getAiProvider()
                    + " model=" + config.getAiModel()
                    + " endpoint=" + baseUrl);
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AIClientException("AI HTTP " + response.statusCode() + ": "
                        + LoggingService.sanitize(response.body()));
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new AIClientException("AI returned empty content");
            }
            return stripMarkdownFences(content.asText());
        } catch (AIClientException e) {
            throw e;
        } catch (Exception e) {
            throw new AIClientException("AI request failed: " + e.getMessage(), e);
        }
    }

    /** Gemini sometimes wraps JSON in ```json fences — remove them. */
    static String stripMarkdownFences(String text) {
        if (text == null) {
            return "";
        }
        String s = text.trim();
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            if (firstNl > 0) {
                s = s.substring(firstNl + 1);
            }
            int end = s.lastIndexOf("```");
            if (end >= 0) {
                s = s.substring(0, end);
            }
            s = s.trim();
        }
        return s;
    }
}

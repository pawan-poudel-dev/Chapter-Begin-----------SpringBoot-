package com.aidesktopagent.actions;

import com.aidesktopagent.logging.LoggingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Parses structured JSON produced by the AI into an {@link ActionPlan}.
 * Rejects unknown action types and malformed payloads; never executes anything.
 */
public final class ActionParser {

    private final ObjectMapper mapper;
    private final LoggingService log;

    public ActionParser() {
        this(new ObjectMapper(), LoggingService.forComponent("ActionParser"));
    }

    public ActionParser(ObjectMapper mapper, LoggingService log) {
        this.mapper = mapper != null ? mapper : new ObjectMapper();
        this.log = log != null ? log : LoggingService.forComponent("ActionParser");
    }

    /**
     * Parse AI JSON response into an ActionPlan.
     *
     * @param originalCommand the user text that produced this response
     * @param json            raw JSON from the LLM
     * @return parsed plan (may be empty if no valid actions)
     * @throws ActionParseException if the JSON is fundamentally unusable
     */
    public ActionPlan parse(String originalCommand, String json) throws ActionParseException {
        if (json == null || json.isBlank()) {
            throw new ActionParseException("AI response is empty");
        }

        String cleaned = extractJsonObject(json.trim());
        final JsonNode root;
        try {
            root = mapper.readTree(cleaned);
        } catch (Exception e) {
            throw new ActionParseException("Invalid JSON from AI: " + e.getMessage(), e);
        }

        if (root == null || !root.isObject()) {
            throw new ActionParseException("AI response root must be a JSON object");
        }

        JsonNode actionsNode = root.get("actions");
        if (actionsNode == null || !actionsNode.isArray()) {
            throw new ActionParseException("Missing or invalid 'actions' array");
        }

        String rationale = textOrNull(root.get("rationale"));
        List<Action> actions = new ArrayList<>();

        for (int i = 0; i < actionsNode.size(); i++) {
            JsonNode node = actionsNode.get(i);
            Optional<Action> action = parseSingleAction(node, i);
            action.ifPresent(actions::add);
        }

        log.info("Parsed " + actions.size() + " action(s) from AI response for: " + originalCommand);
        return new ActionPlan(originalCommand, actions, rationale);
    }

    private Optional<Action> parseSingleAction(JsonNode node, int index) {
        if (node == null || !node.isObject()) {
            log.warn("Skipping non-object action at index " + index);
            return Optional.empty();
        }

        String typeRaw = textOrNull(node.get("type"));
        Optional<ActionType> typeOpt = ActionType.tryParse(typeRaw);
        if (typeOpt.isEmpty()) {
            log.warn("Skipping unknown action type '" + typeRaw + "' at index " + index);
            return Optional.empty();
        }

        String target = textOrNull(node.get("target"));
        Map<String, String> params = new LinkedHashMap<>();

        JsonNode paramsNode = node.get("parameters");
        if (paramsNode != null && paramsNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = paramsNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> e = fields.next();
                if (e.getValue() != null && e.getValue().isValueNode()) {
                    params.put(e.getKey(), e.getValue().asText());
                }
            }
        }

        // Common alternate field names
        if (target == null) {
            target = textOrNull(node.get("name"));
        }
        if (target == null) {
            target = textOrNull(node.get("path"));
        }
        if (target == null) {
            target = textOrNull(node.get("url"));
        }
        if (target == null) {
            target = textOrNull(node.get("text"));
        }
        if (target == null) {
            target = textOrNull(node.get("application"));
        }

        Action action = Action.builder(typeOpt.get())
                .target(target)
                .parameters(params)
                .build();
        return Optional.of(action);
    }

    /**
     * LLMs sometimes wrap JSON in markdown fences; strip them.
     */
    static String extractJsonObject(String raw) {
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            int lastFence = s.lastIndexOf("```");
            if (firstNl > 0 && lastFence > firstNl) {
                s = s.substring(firstNl + 1, lastFence).trim();
            }
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return s.substring(start, end + 1);
        }
        return s;
    }

    private static String textOrNull(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        String t = n.asText();
        return t != null && !t.isBlank() ? t.trim() : null;
    }
}

package com.aidesktopagent.verification;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Day 60 acceptance feature matrix — honest status codes.
 */
public final class FeatureMatrix {

    public enum Status {
        IMPLEMENTED_TESTED,
        IMPLEMENTED_PARTIAL,
        NEEDS_CONFIG,
        NOT_IMPLEMENTED,
        OS_OR_SERVICE_LIMIT
    }

    private final Map<String, Status> features = new LinkedHashMap<>();

    public FeatureMatrix() {
        features.put("Start Java app", Status.IMPLEMENTED_TESTED);
        features.put("Robot avatar (mood + ASCII)", Status.IMPLEMENTED_TESTED);
        features.put("Voice command pipeline", Status.IMPLEMENTED_PARTIAL);
        features.put("Spoken TTS response", Status.IMPLEMENTED_PARTIAL);
        features.put("Open approved application", Status.IMPLEMENTED_PARTIAL);
        features.put("Web search", Status.IMPLEMENTED_TESTED);
        features.put("YouTube search/open", Status.IMPLEMENTED_PARTIAL);
        features.put("Spotify control", Status.NEEDS_CONFIG);
        features.put("Create/inspect folder", Status.IMPLEMENTED_TESTED);
        features.put("Open project in IDE", Status.NEEDS_CONFIG);
        features.put("Code proposal / safe edit", Status.IMPLEMENTED_TESTED);
        features.put("Run project build/tests", Status.IMPLEMENTED_PARTIAL);
        features.put("Recover from test failure analysis", Status.IMPLEMENTED_TESTED);
        features.put("Cancel active task", Status.IMPLEMENTED_TESTED);
        features.put("Destructive confirmation", Status.IMPLEMENTED_TESTED);
        features.put("Logs without secrets", Status.IMPLEMENTED_TESTED);
        features.put("Preferences persist", Status.IMPLEMENTED_TESTED);
        features.put("Full JavaFX animated UI", Status.NOT_IMPLEMENTED);
        features.put("Scheduled workflows", Status.IMPLEMENTED_PARTIAL);
        features.put("Plugin framework", Status.IMPLEMENTED_TESTED);
        features.put("Permission registry", Status.IMPLEMENTED_TESTED);
        features.put("Offline mode", Status.IMPLEMENTED_TESTED);
        features.put("Project index", Status.IMPLEMENTED_TESTED);
        features.put("Multi-step plan validation", Status.IMPLEMENTED_TESTED);
    }

    public Map<String, Status> all() {
        return Map.copyOf(features);
    }

    public String render() {
        StringBuilder sb = new StringBuilder("Feature matrix (Day 60):\n");
        features.forEach((k, v) -> sb.append(String.format("  %-36s %s%n", k, v)));
        return sb.toString();
    }
}

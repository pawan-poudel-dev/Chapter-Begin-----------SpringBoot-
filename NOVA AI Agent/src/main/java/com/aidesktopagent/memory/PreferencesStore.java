package com.aidesktopagent.memory;

import com.aidesktopagent.logging.LoggingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * User-approved preferences only (Day 20). Never stores passwords or API keys.
 */
public final class PreferencesStore {

    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, String> data = new LinkedHashMap<>();
    private final LoggingService log;

    public PreferencesStore(Path file) {
        this(file, LoggingService.forComponent("Preferences"));
    }

    public PreferencesStore(Path file, LoggingService log) {
        this.file = Objects.requireNonNull(file);
        this.log = log != null ? log : LoggingService.forComponent("Preferences");
        loadQuietly();
    }

    private void loadQuietly() {
        try {
            if (Files.isRegularFile(file)) {
                var node = mapper.readTree(Files.readString(file));
                node.fields().forEachRemaining(e -> {
                    if (e.getValue().isTextual() && isAllowedKey(e.getKey())) {
                        data.put(e.getKey(), e.getValue().asText());
                    }
                });
            }
        } catch (Exception e) {
            log.warn("Could not load preferences: " + e.getMessage());
        }
    }

    public synchronized void set(String key, String value) {
        if (!isAllowedKey(key)) {
            throw new IllegalArgumentException("Preference key not allowed: " + key);
        }
        if (looksSecret(key) || looksSecret(value)) {
            throw new IllegalArgumentException("Refusing to store secret-like values in preferences");
        }
        data.put(key, value != null ? value : "");
        persist();
    }

    public synchronized Optional<String> get(String key) {
        return Optional.ofNullable(data.get(key));
    }

    public synchronized Map<String, String> all() {
        return Map.copyOf(data);
    }

    public synchronized void clear() {
        data.clear();
        persist();
    }

    public synchronized void remove(String key) {
        data.remove(key);
        persist();
    }

    private void persist() {
        try {
            Files.createDirectories(file.getParent() != null ? file.getParent() : Path.of("."));
            ObjectNode node = mapper.createObjectNode();
            data.forEach(node::put);
            Files.writeString(file, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node));
            log.info("Preferences saved (" + data.size() + " keys)");
        } catch (IOException e) {
            log.warn("Failed to save preferences: " + e.getMessage());
        }
    }

    private static boolean isAllowedKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String k = key.toLowerCase();
        return k.equals("browser")
                || k.equals("ide")
                || k.equals("voice")
                || k.equals("language")
                || k.equals("folder")
                || k.equals("theme")
                || k.equals("reduced_motion")
                || k.startsWith("pref.");
    }

    private static boolean looksSecret(String s) {
        if (s == null) {
            return false;
        }
        String l = s.toLowerCase();
        return l.contains("password") || l.contains("secret") || l.contains("api_key")
                || l.contains("token") || l.startsWith("sk-");
    }
}

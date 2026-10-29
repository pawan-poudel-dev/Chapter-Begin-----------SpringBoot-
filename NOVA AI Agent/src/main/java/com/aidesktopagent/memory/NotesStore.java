package com.aidesktopagent.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Simple named notes under data/notes.json — no secrets. */
public final class NotesStore {

    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, String> notes = new LinkedHashMap<>();

    public NotesStore(Path file) {
        this.file = file;
        load();
    }

    private void load() {
        try {
            if (!Files.isRegularFile(file)) {
                return;
            }
            var node = mapper.readTree(Files.readString(file));
            node.fields().forEachRemaining(e -> {
                if (e.getValue().isTextual()) {
                    notes.put(e.getKey().toLowerCase(), e.getValue().asText());
                }
            });
        } catch (Exception ignored) {
        }
    }

    private void persist() {
        try {
            Files.createDirectories(file.getParent() != null ? file.getParent() : Path.of("."));
            ObjectNode n = mapper.createObjectNode();
            notes.forEach(n::put);
            Files.writeString(file, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(n));
        } catch (Exception ignored) {
        }
    }

    public void save(String name, String body) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Note name required");
        }
        if (body != null && body.length() > 20_000) {
            throw new IllegalArgumentException("Note too large");
        }
        String key = name.trim().toLowerCase().replaceAll("[^a-z0-9._-]+", "-");
        notes.put(key, body != null ? body : "");
        persist();
    }

    public Optional<String> read(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(notes.get(name.trim().toLowerCase()));
    }

    public Map<String, String> all() {
        return Map.copyOf(notes);
    }

    public String listSummary() {
        if (notes.isEmpty()) {
            return "No notes yet. Say: save note shopping milk and eggs";
        }
        StringBuilder sb = new StringBuilder("Notes (" + notes.size() + "):\n");
        notes.forEach((k, v) -> {
            String preview = v.length() > 60 ? v.substring(0, 60) + "…" : v;
            sb.append("  - ").append(k).append(": ").append(preview).append('\n');
        });
        return sb.toString().trim();
    }
}

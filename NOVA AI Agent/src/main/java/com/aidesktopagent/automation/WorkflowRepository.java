package com.aidesktopagent.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Persists named workflows (Day 31). */
public final class WorkflowRepository {

    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, NamedWorkflow> workflows = new ConcurrentHashMap<>();

    public WorkflowRepository(Path file) {
        this.file = file;
        loadQuietly();
    }

    public void save(NamedWorkflow workflow) {
        workflows.put(workflow.getName().toLowerCase(), workflow);
        persist();
    }

    public Optional<NamedWorkflow> get(String name) {
        return Optional.ofNullable(workflows.get(name.toLowerCase()));
    }

    public Collection<NamedWorkflow> all() {
        return workflows.values();
    }

    public boolean delete(String name) {
        boolean removed = workflows.remove(name.toLowerCase()) != null;
        if (removed) {
            persist();
        }
        return removed;
    }

    private void loadQuietly() {
        try {
            if (!Files.isRegularFile(file)) {
                return;
            }
            JsonNode root = mapper.readTree(Files.readString(file));
            if (!root.isArray()) {
                return;
            }
            for (JsonNode n : root) {
                String name = n.path("name").asText();
                String desc = n.path("description").asText("");
                ArrayList<String> steps = new ArrayList<>();
                for (JsonNode s : n.path("steps")) {
                    steps.add(s.asText());
                }
                if (!name.isBlank() && !steps.isEmpty()) {
                    workflows.put(name.toLowerCase(), new NamedWorkflow(name, steps, desc));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void persist() {
        try {
            Files.createDirectories(file.getParent() != null ? file.getParent() : Path.of("."));
            ArrayNode arr = mapper.createArrayNode();
            for (NamedWorkflow w : workflows.values()) {
                ObjectNode n = mapper.createObjectNode();
                n.put("name", w.getName());
                n.put("description", w.getDescription());
                ArrayNode steps = n.putArray("steps");
                w.getSteps().forEach(steps::add);
                arr.add(n);
            }
            Files.writeString(file, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(arr));
        } catch (Exception ignored) {
        }
    }
}

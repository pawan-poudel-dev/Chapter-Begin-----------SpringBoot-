package com.aidesktopagent.actions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A single validated action proposed by the AI.
 * Immutable. Never contains executable code — only structured parameters.
 */
public final class Action {

    private final String id;
    private final ActionType type;
    private final String target;
    private final Map<String, String> parameters;

    private Action(Builder b) {
        this.id = b.id != null ? b.id : UUID.randomUUID().toString();
        this.type = Objects.requireNonNull(b.type, "type");
        this.target = b.target;
        this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(b.parameters));
    }

    public String getId() {
        return id;
    }

    public ActionType getType() {
        return type;
    }

    /** Primary target (app name, URL, file path, text to type, etc.). */
    public Optional<String> getTarget() {
        return Optional.ofNullable(target).filter(s -> !s.isBlank());
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public Optional<String> getParameter(String key) {
        return Optional.ofNullable(parameters.get(key));
    }

    public boolean isDestructive() {
        return type.isDestructive();
    }

    @Override
    public String toString() {
        return type + (target != null && !target.isBlank() ? " → " + target : "")
                + (parameters.isEmpty() ? "" : " " + parameters);
    }

    public static Builder builder(ActionType type) {
        return new Builder(type);
    }

    public static final class Builder {
        private String id;
        private final ActionType type;
        private String target;
        private final Map<String, String> parameters = new LinkedHashMap<>();

        private Builder(ActionType type) {
            this.type = type;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder target(String target) {
            this.target = target;
            return this;
        }

        public Builder parameter(String key, String value) {
            if (key != null && value != null) {
                this.parameters.put(key, value);
            }
            return this;
        }

        public Builder parameters(Map<String, String> params) {
            if (params != null) {
                this.parameters.putAll(params);
            }
            return this;
        }

        public Action build() {
            return new Action(this);
        }
    }
}

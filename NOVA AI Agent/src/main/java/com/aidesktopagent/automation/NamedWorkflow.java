package com.aidesktopagent.automation;

import java.util.List;
import java.util.Objects;

/** User-defined reusable sequence of natural-language steps (Day 31). */
public final class NamedWorkflow {

    private final String name;
    private final List<String> steps;
    private final String description;

    public NamedWorkflow(String name, List<String> steps, String description) {
        this.name = Objects.requireNonNull(name);
        this.steps = List.copyOf(steps);
        this.description = description != null ? description : "";
    }

    public String getName() {
        return name;
    }

    public List<String> getSteps() {
        return steps;
    }

    public String getDescription() {
        return description;
    }
}

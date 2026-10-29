package com.aidesktopagent.actions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Ordered list of actions produced by the AI for a single user command.
 */
public final class ActionPlan {

    private final String originalCommand;
    private final List<Action> actions;
    private final String rationale;

    public ActionPlan(String originalCommand, List<Action> actions, String rationale) {
        this.originalCommand = originalCommand;
        this.actions = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(actions, "actions")));
        this.rationale = rationale;
    }

    public static ActionPlan empty(String originalCommand) {
        return new ActionPlan(originalCommand, List.of(), "No actions");
    }

    public static ActionPlan of(String originalCommand, Action... actions) {
        return new ActionPlan(originalCommand, List.of(actions), null);
    }

    public String getOriginalCommand() {
        return originalCommand;
    }

    public List<Action> getActions() {
        return actions;
    }

    public String getRationale() {
        return rationale;
    }

    public boolean isEmpty() {
        return actions.isEmpty();
    }

    public int size() {
        return actions.size();
    }

    @Override
    public String toString() {
        if (actions.isEmpty()) {
            return "ActionPlan{empty, command='" + originalCommand + "'}";
        }
        StringBuilder sb = new StringBuilder("ActionPlan{");
        sb.append("command='").append(originalCommand).append("', actions=[");
        for (int i = 0; i < actions.size(); i++) {
            if (i > 0) sb.append("; ");
            sb.append(actions.get(i));
        }
        sb.append("]}");
        return sb.toString();
    }
}

package com.aidesktopagent.agent;

import com.aidesktopagent.actions.Action;
import com.aidesktopagent.actions.ActionPlan;
import com.aidesktopagent.actions.ActionType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Multi-step plan validation: conflicts, size limits, preconditions (Day 56).
 */
public final class PlanValidator {

    public static final int MAX_ACTIONS = 12;

    public record Issue(String message, boolean critical) {
    }

    public record Result(ActionPlan plan, List<Issue> issues, boolean acceptable) {
    }

    public Result validate(ActionPlan plan) {
        List<Issue> issues = new ArrayList<>();
        if (plan == null || plan.getActions().isEmpty()) {
            issues.add(new Issue("Empty plan", true));
            return new Result(plan, issues, false);
        }
        if (plan.getActions().size() > MAX_ACTIONS) {
            issues.add(new Issue("Plan exceeds max actions (" + MAX_ACTIONS + ")", true));
        }

        Set<ActionType> types = new HashSet<>();
        for (Action a : plan.getActions()) {
            types.add(a.getType());
        }
        if (types.contains(ActionType.DELETE_FILE) && types.contains(ActionType.CREATE_FILE)
                && plan.getActions().size() > 1) {
            issues.add(new Issue("Plan mixes create and delete — review carefully", false));
        }
        if (types.contains(ActionType.RUN_COMMAND) && plan.getActions().size() > 1) {
            issues.add(new Issue("RUN_COMMAND with other steps increases risk", false));
        }

        // Preconditions: APPLY requires a path
        for (Action a : plan.getActions()) {
            if (a.getType() == ActionType.APPLY_CODE_CHANGE && a.getTarget().isEmpty()) {
                issues.add(new Issue("APPLY_CODE_CHANGE missing path", true));
            }
        }

        boolean critical = issues.stream().anyMatch(Issue::critical);
        return new Result(plan, issues, !critical);
    }
}

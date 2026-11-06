package com.aidesktopagent.agent;

import com.aidesktopagent.actions.Action;
import com.aidesktopagent.actions.ActionPlan;
import com.aidesktopagent.actions.ActionResult;
import com.aidesktopagent.logging.LoggingService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Runs multi-step validated action plans sequentially (Day 15).
 * Stops on critical failure; records per-step results.
 */
public final class WorkflowEngine {

    private final Agent agent;
    private final LoggingService log;

    public WorkflowEngine(Agent agent) {
        this(agent, LoggingService.forComponent("Workflow"));
    }

    public WorkflowEngine(Agent agent, LoggingService log) {
        this.agent = Objects.requireNonNull(agent);
        this.log = log != null ? log : LoggingService.forComponent("Workflow");
    }

    public WorkflowResult runPlan(ActionPlan plan, boolean stopOnFailure) {
        List<ActionResult> results = new ArrayList<>();
        if (plan == null || plan.getActions().isEmpty()) {
            return new WorkflowResult(List.of(), false, "Empty plan");
        }
        for (Action action : plan.getActions()) {
            try {
                List<ActionResult> step = agent.handle(action.getType().name() + " "
                        + action.getTarget().orElse(""));
                // Prefer direct execute path when possible — handle re-plans from text.
                // For robustness, execute via agent.handle with a synthetic command is weak;
                // use plan+existing executor through agent for the whole plan once.
                results.addAll(step);
                if (stopOnFailure && step.stream().anyMatch(r -> !r.isSuccess())) {
                    return new WorkflowResult(results, false, "Stopped after failed step: " + action);
                }
            } catch (Exception e) {
                results.add(ActionResult.failure(action.getType(), e.getMessage()));
                if (stopOnFailure) {
                    return new WorkflowResult(results, false, e.getMessage());
                }
            }
        }
        boolean allOk = results.stream().allMatch(ActionResult::isSuccess);
        return new WorkflowResult(results, allOk, allOk ? "Workflow completed" : "Workflow finished with errors");
    }

    /**
     * Execute a list of natural-language steps as sequential agent.handle calls.
     */
    public WorkflowResult runSteps(List<String> commands, boolean stopOnFailure) {
        List<ActionResult> results = new ArrayList<>();
        if (commands == null || commands.isEmpty()) {
            return new WorkflowResult(List.of(), false, "No steps");
        }
        for (String cmd : commands) {
            try {
                log.info("Workflow step: " + cmd);
                List<ActionResult> step = agent.handle(cmd);
                results.addAll(step);
                if (stopOnFailure && step.stream().anyMatch(r -> !r.isSuccess())) {
                    return new WorkflowResult(results, false, "Stopped at: " + cmd);
                }
            } catch (AgentException e) {
                results.add(ActionResult.failure(
                        com.aidesktopagent.actions.ActionType.RUN_COMMAND, e.getMessage()));
                if (stopOnFailure) {
                    return new WorkflowResult(results, false, e.getMessage());
                }
            }
        }
        boolean allOk = results.stream().allMatch(ActionResult::isSuccess);
        return new WorkflowResult(results, allOk,
                allOk ? "All " + commands.size() + " steps succeeded" : "Completed with failures");
    }

    public record WorkflowResult(List<ActionResult> results, boolean success, String summary) {
    }
}

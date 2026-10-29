package com.aidesktopagent.agent;

import com.aidesktopagent.actions.Action;
import com.aidesktopagent.actions.ActionParseException;
import com.aidesktopagent.actions.ActionParser;
import com.aidesktopagent.actions.ActionPlan;
import com.aidesktopagent.actions.ActionResult;
import com.aidesktopagent.actions.ActionType;
import com.aidesktopagent.actions.ActionValidator;
import com.aidesktopagent.actions.ActionValidator.ValidationResult;
import com.aidesktopagent.ai.AIClient;
import com.aidesktopagent.ai.AIClientException;
import com.aidesktopagent.ai.CommandNormalizer;
import com.aidesktopagent.computer.ActionExecutor;
import com.aidesktopagent.logging.LoggingService;
import com.aidesktopagent.security.ActionAuthorization;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Core agent orchestrator — fast path for offline rules, multi-command chains,
 * context resolution, and permission-aware execution.
 *
 * <pre>
 *   text → normalize → resolve refs → AIClient → parse → validate → confirm → execute
 * </pre>
 */
public final class Agent {

    private final AIClient aiClient;
    private final ActionParser parser;
    private final ActionValidator validator;
    private final ActionExecutor executor;
    private final LoggingService log;
    private final Function<Action, Boolean> confirmationHandler;
    private final CommandNormalizer normalizer;
    private final ConversationContext context;
    private final PlanValidator planValidator;
    private final CancellationCoordinator cancellation;
    private final boolean verboseLogging;
    private final ActionAuthorization authorization;

    public Agent(AIClient aiClient,
                 ActionParser parser,
                 ActionValidator validator,
                 ActionExecutor executor,
                 Function<Action, Boolean> confirmationHandler,
                 LoggingService log) {
        this(aiClient, parser, validator, executor, confirmationHandler, log,
                new CommandNormalizer(), new ConversationContext(), new PlanValidator(), null, false, null);
    }

    public Agent(AIClient aiClient,
                 ActionParser parser,
                 ActionValidator validator,
                 ActionExecutor executor,
                 Function<Action, Boolean> confirmationHandler,
                 LoggingService log,
                 CommandNormalizer normalizer,
                 ConversationContext context,
                 PlanValidator planValidator,
                 CancellationCoordinator cancellation,
                 boolean verboseLogging,
                 ActionAuthorization authorization) {
        this.aiClient = Objects.requireNonNull(aiClient, "aiClient");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.executor = executor;
        this.confirmationHandler = confirmationHandler != null ? confirmationHandler : a -> false;
        this.log = log != null ? log : LoggingService.forComponent("Agent");
        this.normalizer = normalizer != null ? normalizer : new CommandNormalizer();
        this.context = context != null ? context : new ConversationContext();
        this.planValidator = planValidator != null ? planValidator : new PlanValidator();
        this.cancellation = cancellation;
        this.verboseLogging = verboseLogging;
        this.authorization = authorization; // optional; PermissionGuardedExecutor is primary
    }

    public ConversationContext getContext() {
        return context;
    }

    public ActionPlan plan(String userCommand) throws AgentException {
        String prepared = prepareCommand(userCommand);
        if (verboseLogging) {
            log.commandReceived(prepared);
        }
        try {
            String json = aiClient.planActions(prepared);
            ActionPlan raw = parser.parse(prepared, json);
            if (verboseLogging) {
                for (Action a : raw.getActions()) {
                    log.actionPlanned(a.getId(), a.toString());
                }
            }
            ValidationResult vr = validator.validate(raw);
            if (vr.hasRejections() && verboseLogging) {
                log.warn("Rejected actions: " + vr.getRejections());
            }
            if (!vr.hasAcceptedActions()) {
                throw new AgentException("No valid actions for: " + prepared
                        + (vr.hasRejections() ? " (" + String.join("; ", vr.getRejections()) + ")" : ""));
            }
            var planCheck = planValidator.validate(vr.getPlan());
            if (!planCheck.acceptable()) {
                throw new AgentException("Plan rejected: "
                        + planCheck.issues().stream().map(PlanValidator.Issue::message)
                        .reduce((a, b) -> a + "; " + b).orElse("invalid"));
            }
            return vr.getPlan();
        } catch (AIClientException | ActionParseException e) {
            log.failure("plan", e);
            throw new AgentException("Planning failed: " + e.getMessage(), e);
        }
    }

    /**
     * Full pipeline. Supports chained commands separated by " and then " / " then " / " && ".
     */
    public List<ActionResult> handle(String userCommand) throws AgentException {
        if (executor == null) {
            throw new AgentException("No ActionExecutor configured");
        }
        if (cancellation != null && cancellation.isCancelled()) {
            throw new AgentException("Agent is stopped — say 'resume agent' or restart");
        }
        if (normalizer.isLowConfidence(userCommand)) {
            throw new AgentException("I didn't catch that clearly. Please repeat the command.");
        }
        if (normalizer.isDestructiveAmbiguous(userCommand)) {
            throw new AgentException("That delete/remove request is too vague. Name the file or folder.");
        }

        List<String> chain = splitChain(userCommand);
        List<ActionResult> all = new ArrayList<>();
        for (String part : chain) {
            if (cancellation != null && cancellation.isCancelled()) {
                all.add(ActionResult.cancelled(ActionType.RUN_COMMAND, "Cancelled mid-chain"));
                break;
            }
            all.addAll(handleSingle(part.trim()));
        }
        return all;
    }

    private List<ActionResult> handleSingle(String userCommand) throws AgentException {
        ActionPlan plan = plan(userCommand);
        List<ActionResult> results = new ArrayList<>(plan.getActions().size());

        for (Action action : plan.getActions()) {
            if (cancellation != null && cancellation.isCancelled()) {
                results.add(ActionResult.cancelled(action.getType(), "Cancelled"));
                break;
            }
            if (action.isDestructive()) {
                boolean ok = Boolean.TRUE.equals(confirmationHandler.apply(action));
                if (!ok) {
                    results.add(ActionResult.cancelled(action.getType(),
                            "User declined confirmation for " + action.getType()));
                    continue;
                }
            }
            try {
                if (authorization != null) {
                    authorization.requireAllowed(action);
                }
                ActionResult result = executor.execute(action);
                results.add(result);
                rememberContext(action, result);
                if (result.isSuccess()) {
                    if (verboseLogging) {
                        log.actionExecuted(action.getId(), result.getMessage());
                    }
                } else {
                    log.failure("execute", result.getMessage());
                    // Fail-closed: do not continue dependent steps after a failed action
                    if (plan.getActions().size() > 1) {
                        log.warn("Stopping remaining plan steps after failure of " + action.getType());
                        break;
                    }
                }
            } catch (SecurityException e) {
                log.failure("authorize", e);
                results.add(ActionResult.failure(action.getType(), e.getMessage()));
                break;
            } catch (Exception e) {
                log.failure("execute", e);
                results.add(ActionResult.failure(action.getType(), e.getMessage()));
                if (plan.getActions().size() > 1) {
                    break;
                }
            }
        }
        context.setLastCommand(userCommand);
        return results;
    }

    private String prepareCommand(String userCommand) {
        String cmd = normalizer.normalize(userCommand == null ? "" : userCommand.trim());
        Optional<String> ref = context.resolveReference(cmd);
        if (ref.isPresent()) {
            // Replace vague references with concrete targets
            String lower = cmd.toLowerCase();
            if (lower.contains("that file") || lower.contains("the file") || lower.equals("it")) {
                cmd = cmd.replaceAll("(?i)\\b(that file|the file|it)\\b", ref.get());
            } else if (lower.contains("result")) {
                cmd = "open " + ref.get();
            }
        }
        return cmd;
    }

    private void rememberContext(Action action, ActionResult result) {
        if (!result.isSuccess()) {
            return;
        }
        action.getTarget().ifPresent(t -> {
            if (action.getType() == ActionType.READ_FILE
                    || action.getType() == ActionType.READ_SOURCE_FILE
                    || action.getType() == ActionType.SEARCH_FILES
                    || action.getType() == ActionType.CREATE_FILE
                    || action.getType() == ActionType.CREATE_FOLDER) {
                context.setLastFile(t);
            }
            if (action.getType().isMediaRelated()) {
                context.setLastMediaProvider(
                        action.getType().name().contains("SPOTIFY") ? "spotify" : "youtube");
            }
        });
        if (action.getType() == ActionType.SEARCH_FILES && result.getDetail() != null
                && !result.getDetail().isBlank()) {
            List<ConversationContext.SearchHit> hits = new ArrayList<>();
            for (String line : result.getDetail().split("\n")) {
                String p = line.trim();
                if (!p.isEmpty()) {
                    hits.add(new ConversationContext.SearchHit(p, p));
                }
            }
            context.setLastResults(hits);
        }
    }

    /**
     * Split multi-step utterances without breaking "Open Chrome and search for X"
     * (those stay single — handled by rules).
     */
    static List<String> splitChain(String command) {
        if (command == null || command.isBlank()) {
            return List.of();
        }
        String c = command.trim();
        // Explicit sequence markers only
        if (c.matches("(?i).+\\s+and then\\s+.+") || c.matches("(?i).+\\s+then\\s+.+")
                || c.contains(" && ")) {
            String[] parts = c.split("(?i)\\s+and then\\s+|\\s+then\\s+|\\s+&&\\s+");
            List<String> out = new ArrayList<>();
            for (String p : parts) {
                if (!p.isBlank()) {
                    out.add(p.trim());
                }
            }
            return out.isEmpty() ? List.of(c) : out;
        }
        return List.of(c);
    }
}

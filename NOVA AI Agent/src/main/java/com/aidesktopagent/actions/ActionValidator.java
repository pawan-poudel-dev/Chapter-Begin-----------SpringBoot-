package com.aidesktopagent.actions;

import com.aidesktopagent.logging.LoggingService;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates every action before execution.
 * Rejects unknown types, missing required fields, path traversal, and unsafe RUN_COMMAND usage.
 * The AI never executes anything — only this validated plan is eligible for the executor.
 */
public final class ActionValidator {

    private static final Set<ActionType> KNOWN = EnumSet.allOf(ActionType.class);

    /** Allowed executables for RUN_COMMAND (basename only). Empty = RUN_COMMAND disabled. */
    private static final Set<String> ALLOWED_COMMANDS = Set.of(
            "ls", "dir", "pwd", "echo", "date", "whoami", "uname", "cat", "head", "tail"
    );

    private static final Pattern SAFE_APP_NAME = Pattern.compile("^[\\w .\\-+()]+$");
    private static final Pattern SAFE_KEY = Pattern.compile("^[A-Za-z0-9_+\\-]+$");

    private final LoggingService log;
    private final Path workspaceRoot;

    public ActionValidator() {
        this(LoggingService.forComponent("ActionValidator"), Path.of(".").toAbsolutePath().normalize());
    }

    public ActionValidator(LoggingService log, Path workspaceRoot) {
        this.log = log != null ? log : LoggingService.forComponent("ActionValidator");
        this.workspaceRoot = workspaceRoot != null
                ? workspaceRoot.toAbsolutePath().normalize()
                : Path.of(".").toAbsolutePath().normalize();
    }

    /**
     * Validate an entire plan. Returns a new plan containing only accepted actions.
     * Throws if the plan is completely unusable (optional strict mode via flag).
     */
    public ValidationResult validate(ActionPlan plan) {
        if (plan == null) {
            return ValidationResult.rejected("Plan is null");
        }

        List<Action> accepted = new ArrayList<>();
        List<String> rejections = new ArrayList<>();

        for (Action action : plan.getActions()) {
            String reason = validateOne(action);
            if (reason == null) {
                accepted.add(action);
                log.actionValidated(action.getId(), true);
            } else {
                rejections.add(action.getType() + ": " + reason);
                log.actionValidated(action.getId(), false);
                log.warn("Rejected action " + action + " — " + reason);
            }
        }

        ActionPlan validated = new ActionPlan(plan.getOriginalCommand(), accepted, plan.getRationale());
        return new ValidationResult(validated, rejections);
    }

    /**
     * @return null if valid, otherwise a human-readable rejection reason
     */
    String validateOne(Action action) {
        if (action == null) {
            return "Action is null";
        }
        ActionType type = action.getType();
        if (!KNOWN.contains(type)) {
            return "Unknown action type";
        }

        return switch (type) {
            case OPEN_APPLICATION, CLOSE_APPLICATION -> requireSafeAppName(action);
            case OPEN_WEBSITE -> requireHttpUrl(action);
            case SEARCH_WEB, SEARCH_YOUTUBE, SEARCH_SPOTIFY -> requireNonBlankTarget(action, "search query");
            case OPEN_FOLDER, CREATE_FOLDER, CREATE_FILE, READ_FILE, DELETE_FILE -> requireSafePath(action, true);
            case MOVE_FILE, COPY_FILE -> validateCopyMove(action);
            case TYPE_TEXT -> requireNonBlankTarget(action, "text");
            case PRESS_KEY -> requireSafeKey(action);
            case PLAY_YOUTUBE, OPEN_YOUTUBE_VIDEO, PLAY_SPOTIFY -> requireNonBlankTarget(action, "query or URL");
            case OPEN_SPOTIFY -> null; // target optional (opens home)
            case PAUSE_MEDIA, RESUME_MEDIA, STOP_MEDIA, NEXT_TRACK, PREVIOUS_TRACK, GET_MEDIA_STATUS -> null;
            case ANALYZE_PROJECT, LIST_SOURCE_FILES, RUN_BUILD -> null;
            case READ_SOURCE_FILE, PROPOSE_CODE_CHANGE, APPLY_CODE_CHANGE -> requireNonBlankTarget(action, "source path");
            case EXPLAIN_ERROR -> requireNonBlankTarget(action, "error or stack trace");
            case OPEN_IN_IDE, SEARCH_FILES, SUMMARIZE_FOLDER, GET_PREFERENCE, GENERATE_PROJECT -> null;
            case SET_PREFERENCE -> requireNonBlankTarget(action, "preference key");
            case CLIPBOARD_READ, GET_TIME, SYSTEM_INFO, LIST_NOTES, SHOW_HELP, GIT_STATUS, CHAT_REPLY -> null;
            case CLIPBOARD_WRITE, CALCULATE, SAVE_NOTE, SET_TIMER, WRITE_SNIPPET -> requireNonBlankTarget(action, "value");
            case READ_NOTE -> requireNonBlankTarget(action, "note name");
            case RUN_COMMAND -> validateRunCommand(action);
        };
    }

    private String requireNonBlankTarget(Action action, String label) {
        if (action.getTarget().isEmpty()) {
            return "Missing " + label;
        }
        return null;
    }

    private String requireSafeAppName(Action action) {
        String t = action.getTarget().orElse(null);
        if (t == null || t.isBlank()) {
            return "Missing application name";
        }
        if (!SAFE_APP_NAME.matcher(t).matches()) {
            return "Application name contains unsafe characters";
        }
        if (t.length() > 128) {
            return "Application name too long";
        }
        return null;
    }

    private String requireHttpUrl(Action action) {
        String t = action.getTarget().orElse(null);
        if (t == null || t.isBlank()) {
            return "Missing URL";
        }
        // Allow bare domains — executor will add https://
        String candidate = t;
        if (!candidate.contains("://")) {
            candidate = "https://" + candidate;
        }
        try {
            URI uri = URI.create(candidate);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return "Only http/https URLs are allowed";
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                return "URL must have a host";
            }
        } catch (Exception e) {
            return "Invalid URL: " + e.getMessage();
        }
        return null;
    }

    private String requireSafePath(Action action, boolean single) {
        String t = action.getTarget().orElse(null);
        if (t == null || t.isBlank()) {
            return "Missing path";
        }
        return checkPathSafety(t);
    }

    private String validateCopyMove(Action action) {
        String src = action.getTarget().orElse(null);
        String dest = action.getParameter("destination")
                .or(() -> action.getParameter("to"))
                .orElse(null);
        if (src == null || src.isBlank()) {
            return "Missing source path";
        }
        if (dest == null || dest.isBlank()) {
            return "Missing destination path";
        }
        String r1 = checkPathSafety(src);
        if (r1 != null) return "Source: " + r1;
        return checkPathSafety(dest) != null ? "Destination: " + checkPathSafety(dest) : null;
    }

    private String requireSafeKey(Action action) {
        String t = action.getTarget().orElse(null);
        if (t == null || t.isBlank()) {
            return "Missing key name";
        }
        if (!SAFE_KEY.matcher(t).matches()) {
            return "Invalid key name";
        }
        return null;
    }

    private String validateRunCommand(Action action) {
        String cmd = action.getTarget().orElse(null);
        if (cmd == null || cmd.isBlank()) {
            return "Missing command";
        }
        // Only allow a single token from the allow-list — no pipes, redirects, or args with shell metacharacters
        String[] parts = cmd.trim().split("\\s+");
        String base = parts[0].toLowerCase(Locale.ROOT);
        // strip path if present
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        if (!ALLOWED_COMMANDS.contains(base)) {
            return "Command not in allow-list: " + base;
        }
        if (cmd.contains("|") || cmd.contains(";") || cmd.contains("&") || cmd.contains(">") || cmd.contains("<")
                || cmd.contains("`") || cmd.contains("$(") || cmd.contains("\n")) {
            return "Shell metacharacters are not allowed";
        }
        return null;
    }

    private String checkPathSafety(String raw) {
        try {
            if (raw == null || raw.isBlank()) {
                return "Missing path";
            }
            if (raw.indexOf('\0') >= 0) {
                return "Null byte in path";
            }
            Path p = Path.of(raw);
            if (p.isAbsolute()) {
                return "Absolute paths are not permitted; use a workspace-relative path";
            }
            // Reject Windows drive-style paths on any OS
            if (raw.matches("^[A-Za-z]:[\\\\/].*")) {
                return "Absolute paths are not permitted";
            }
            // Component-based traversal check (allows filenames like report..old.txt)
            Path normalized = p.normalize();
            for (Path component : normalized) {
                if (component.toString().equals("..")) {
                    return "Path traversal ('..') is not allowed";
                }
            }
            // If normalize removed leading .. the original had escape intent
            String orig = raw.replace('\\', '/');
            if (orig.startsWith("../") || orig.equals("..") || orig.contains("/../")) {
                return "Path traversal ('..') is not allowed";
            }
            if (normalized.toString().length() > 512) {
                return "Path too long";
            }
        } catch (Exception e) {
            return "Invalid path: " + e.getMessage();
        }
        return null;
    }

    /**
     * Result of validating a plan.
     */
    public static final class ValidationResult {
        private final ActionPlan plan;
        private final List<String> rejections;

        ValidationResult(ActionPlan plan, List<String> rejections) {
            this.plan = plan;
            this.rejections = List.copyOf(rejections);
        }

        public static ValidationResult rejected(String reason) {
            return new ValidationResult(ActionPlan.empty(null), List.of(reason));
        }

        public ActionPlan getPlan() {
            return plan;
        }

        public List<String> getRejections() {
            return rejections;
        }

        public boolean hasAcceptedActions() {
            return plan != null && !plan.isEmpty();
        }

        public boolean hasRejections() {
            return !rejections.isEmpty();
        }
    }
}

package com.aidesktopagent.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Central logging façade for the AI Desktop Agent.
 *
 * <p>Provides structured, domain-specific log methods for the agent lifecycle.
 * Sensitive values (API keys, passwords, secrets, file contents) are redacted
 * before anything is written.
 */
public final class LoggingService {

    private static final Logger log = LoggerFactory.getLogger(LoggingService.class);

    /** Patterns that indicate a value must never appear in logs. */
    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(?i)(api[_-]?key|password|secret|token|authorization|bearer)\\s*[:=]\\s*\\S+",
            Pattern.CASE_INSENSITIVE
    );

    private static final String REDACTED = "[REDACTED]";

    private final String component;

    private LoggingService(String component) {
        this.component = Objects.requireNonNull(component, "component");
    }

    /**
     * Creates a {@link LoggingService} bound to a logical component name
     * (appears in MDC and log messages).
     */
    public static LoggingService forComponent(String component) {
        return new LoggingService(component);
    }

    /**
     * Root / application-level logger.
     */
    public static LoggingService application() {
        return new LoggingService("application");
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void startup(String message) {
        withMdc(() -> log.info("[STARTUP] [{}] {}", component, sanitize(message)));
    }

    public void shutdown(String message) {
        withMdc(() -> log.info("[SHUTDOWN] [{}] {}", component, sanitize(message)));
    }

    // -------------------------------------------------------------------------
    // Agent activity
    // -------------------------------------------------------------------------

    public void commandReceived(String commandSummary) {
        withMdc(() -> log.info("[COMMAND] [{}] received: {}", component, sanitize(commandSummary)));
    }

    public void actionPlanned(String actionId, String description) {
        withMdc(() -> log.info("[ACTION-PLANNED] [{}] id={} desc={}",
                component, sanitize(actionId), sanitize(description)));
    }

    public void actionValidated(String actionId, boolean accepted) {
        withMdc(() -> log.info("[ACTION-VALIDATED] [{}] id={} accepted={}",
                component, sanitize(actionId), accepted));
    }

    public void actionExecuted(String actionId, String resultSummary) {
        withMdc(() -> log.info("[ACTION-EXECUTED] [{}] id={} result={}",
                component, sanitize(actionId), sanitize(resultSummary)));
    }

    public void verification(String actionId, boolean passed, String detail) {
        withMdc(() -> log.info("[VERIFICATION] [{}] id={} passed={} detail={}",
                component, sanitize(actionId), passed, sanitize(detail)));
    }

    public void failure(String context, Throwable error) {
        withMdc(() -> log.error("[FAILURE] [{}] context={} error={}",
                component, sanitize(context), sanitize(error != null ? error.getMessage() : "unknown"), error));
    }

    public void failure(String context, String message) {
        withMdc(() -> log.error("[FAILURE] [{}] context={} message={}",
                component, sanitize(context), sanitize(message)));
    }

    public void recovery(String context, String actionTaken) {
        withMdc(() -> log.warn("[RECOVERY] [{}] context={} action={}",
                component, sanitize(context), sanitize(actionTaken)));
    }

    // -------------------------------------------------------------------------
    // Generic helpers (still sanitized)
    // -------------------------------------------------------------------------

    public void info(String message) {
        withMdc(() -> log.info("[{}] {}", component, sanitize(message)));
    }

    public void debug(String message) {
        withMdc(() -> log.debug("[{}] {}", component, sanitize(message)));
    }

    public void warn(String message) {
        withMdc(() -> log.warn("[{}] {}", component, sanitize(message)));
    }

    public void error(String message) {
        withMdc(() -> log.error("[{}] {}", component, sanitize(message)));
    }

    public void error(String message, Throwable t) {
        withMdc(() -> log.error("[{}] {}", component, sanitize(message), t));
    }

    // -------------------------------------------------------------------------
    // Sanitization
    // -------------------------------------------------------------------------

    /**
     * Removes or masks sensitive material from a log message.
     * Safe to call with {@code null}.
     */
    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }
        String result = SECRET_PATTERN.matcher(input).replaceAll("$1=" + REDACTED);

        // Extra safety: long base64-looking blobs that often are keys
        if (result.length() > 40 && looksLikeSecret(result)) {
            return REDACTED;
        }
        return result;
    }

    private static boolean looksLikeSecret(String s) {
        // Heuristic: high proportion of alphanumeric + limited punctuation
        String trimmed = s.trim();
        if (trimmed.length() < 32) {
            return false;
        }
        long alnum = trimmed.chars().filter(Character::isLetterOrDigit).count();
        return (double) alnum / trimmed.length() > 0.9;
    }

    private void withMdc(Runnable action) {
        String previous = MDC.get("component");
        try {
            MDC.put("component", component);
            action.run();
        } finally {
            if (previous != null) {
                MDC.put("component", previous);
            } else {
                MDC.remove("component");
            }
        }
    }
}

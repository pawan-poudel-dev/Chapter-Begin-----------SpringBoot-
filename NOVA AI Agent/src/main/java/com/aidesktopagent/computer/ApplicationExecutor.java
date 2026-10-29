package com.aidesktopagent.computer;

import com.aidesktopagent.actions.Action;
import com.aidesktopagent.actions.ActionResult;
import com.aidesktopagent.actions.ActionType;
import com.aidesktopagent.logging.LoggingService;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Opens / closes desktop applications via ProcessBuilder and Desktop API.
 * Application names are mapped to common executables; unknown names are tried as-is.
 */
public final class ApplicationExecutor implements ActionExecutor {

    private static final Map<String, List<String>> KNOWN_APPS = Map.ofEntries(
            Map.entry("chrome", List.of("google-chrome", "google-chrome-stable", "chromium", "chromium-browser", "chrome")),
            Map.entry("google chrome", List.of("google-chrome", "google-chrome-stable", "chromium", "chromium-browser")),
            Map.entry("firefox", List.of("firefox")),
            Map.entry("code", List.of("code", "code-insiders")),
            Map.entry("vscode", List.of("code", "code-insiders")),
            Map.entry("visual studio code", List.of("code", "code-insiders")),
            Map.entry("intellij", List.of("idea", "intellij-idea-ultimate", "intellij-idea-community")),
            Map.entry("intellij idea", List.of("idea", "intellij-idea-ultimate", "intellij-idea-community")),
            Map.entry("terminal", List.of("x-terminal-emulator", "gnome-terminal", "konsole", "xterm")),
            Map.entry("nautilus", List.of("nautilus")),
            Map.entry("files", List.of("nautilus", "dolphin", "thunar")),
            Map.entry("calculator", List.of("gnome-calculator", "kcalc", "galculator")),
            Map.entry("gedit", List.of("gedit")),
            Map.entry("notepad", List.of("gedit", "kate", "mousepad"))
    );

    private final LoggingService log;
    private final long timeoutSeconds;

    public ApplicationExecutor() {
        this(LoggingService.forComponent("ApplicationExecutor"), 15);
    }

    public ApplicationExecutor(LoggingService log, long timeoutSeconds) {
        this.log = log != null ? log : LoggingService.forComponent("ApplicationExecutor");
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 15;
    }

    @Override
    public boolean supports(Action action) {
        ActionType t = action.getType();
        return t == ActionType.OPEN_APPLICATION || t == ActionType.CLOSE_APPLICATION;
    }

    @Override
    public ActionResult execute(Action action) {
        String name = action.getTarget().orElse("").trim();
        if (name.isEmpty()) {
            return ActionResult.failure(action.getType(), "No application name provided");
        }

        if (action.getType() == ActionType.CLOSE_APPLICATION) {
            return closeApplication(name);
        }
        return openApplication(name);
    }

    private ActionResult openApplication(String name) {
        List<String> candidates = resolveCandidates(name);
        for (String cmd : candidates) {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                Process p = pb.start();
                // Don't wait for GUI apps to exit — just confirm they started
                boolean finishedQuickly = p.waitFor(500, TimeUnit.MILLISECONDS);
                if (finishedQuickly && p.exitValue() != 0) {
                    continue; // try next candidate
                }
                log.info("Started application via '" + cmd + "'");
                return ActionResult.success(ActionType.OPEN_APPLICATION,
                        name + " opened successfully.", cmd);
            } catch (IOException e) {
                log.debug("Candidate failed: " + cmd + " — " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ActionResult.failure(ActionType.OPEN_APPLICATION, "Interrupted while starting " + name);
            }
        }

        // Last resort: Desktop.open if path-like
        Path asPath = Path.of(name);
        if (Files.exists(asPath) && Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().open(asPath.toFile());
                return ActionResult.success(ActionType.OPEN_APPLICATION, name + " opened via Desktop API.");
            } catch (Exception e) {
                log.debug("Desktop.open failed: " + e.getMessage());
            }
        }

        return ActionResult.failure(ActionType.OPEN_APPLICATION,
                "Could not find or start application: " + name);
    }

    private ActionResult closeApplication(String name) {
        // Best-effort: pkill -f on Linux. Not available / limited on all platforms.
        String pattern = name.toLowerCase(Locale.ROOT).replace(" ", ".*");
        try {
            ProcessBuilder pb = new ProcessBuilder("pkill", "-f", "-i", pattern);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean done = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!done) {
                p.destroyForcibly();
                return ActionResult.failure(ActionType.CLOSE_APPLICATION, "Timeout closing " + name);
            }
            // pkill returns 0 if signals sent, 1 if no process matched
            if (p.exitValue() == 0) {
                return ActionResult.success(ActionType.CLOSE_APPLICATION, name + " close signal sent.");
            }
            return ActionResult.failure(ActionType.CLOSE_APPLICATION,
                    "No running process matched: " + name);
        } catch (IOException e) {
            return ActionResult.failure(ActionType.CLOSE_APPLICATION,
                    "Close not supported on this platform: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ActionResult.failure(ActionType.CLOSE_APPLICATION, "Interrupted");
        }
    }

    private List<String> resolveCandidates(String name) {
        String key = name.toLowerCase(Locale.ROOT).trim();
        List<String> known = KNOWN_APPS.get(key);
        List<String> out = new ArrayList<>();
        if (known != null) {
            out.addAll(known);
        }
        // Also try the raw name and lowercased
        out.add(name);
        if (!name.equals(key)) {
            out.add(key);
        }
        return out;
    }
}

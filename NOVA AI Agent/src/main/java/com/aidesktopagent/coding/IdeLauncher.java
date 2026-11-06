package com.aidesktopagent.coding;

import com.aidesktopagent.logging.LoggingService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Opens an approved project path in a detected IDE.
 */
public final class IdeLauncher {

    private final IdeDetector detector;
    private final ProjectWorkspace workspace;
    private final LoggingService log;

    public IdeLauncher(IdeDetector detector, ProjectWorkspace workspace) {
        this(detector, workspace, LoggingService.forComponent("IdeLauncher"));
    }

    public IdeLauncher(IdeDetector detector, ProjectWorkspace workspace, LoggingService log) {
        this.detector = Objects.requireNonNull(detector);
        this.workspace = workspace; // may be null for pure path open with validation
        this.log = log != null ? log : LoggingService.forComponent("IdeLauncher");
    }

    public LaunchResult openProject(IdeDetector.IdeKind preferred, Path projectPath) {
        Path path = projectPath.toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) {
            return LaunchResult.fail("Not a directory: " + path);
        }
        if (workspace != null && !workspace.contains(path) && !path.equals(workspace.getRoot())) {
            // Allow opening the workspace root or paths inside it
            if (!path.startsWith(workspace.getRoot())) {
                return LaunchResult.fail("Project path outside approved workspace: " + path);
            }
        }

        var ide = preferred != null
                ? detector.find(preferred)
                : detector.detect().stream().findFirst();
        if (ide.isEmpty()) {
            return LaunchResult.fail("No supported IDE found (IntelliJ IDEA or VS Code). "
                    + "Install one or set a custom path via application registry.");
        }

        IdeDetector.IdeInstallation install = ide.get();
        List<String> cmd = new ArrayList<>();
        cmd.add(install.executable().toString());
        cmd.add(path.toString());
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            // Don't wait forever — IDE stays open
            boolean exitedQuickly = proc.waitFor(2, TimeUnit.SECONDS);
            if (exitedQuickly && proc.exitValue() != 0) {
                String err = new String(proc.getInputStream().readAllBytes());
                return LaunchResult.fail("IDE exited with code " + proc.exitValue() + ": " + err);
            }
            log.info("Launched " + install.displayName() + " with " + path);
            return LaunchResult.ok("Opened " + path + " in " + install.displayName());
        } catch (Exception e) {
            return LaunchResult.fail("Failed to launch IDE: " + e.getMessage());
        }
    }

    public record LaunchResult(boolean success, String message) {
        static LaunchResult ok(String m) {
            return new LaunchResult(true, m);
        }

        static LaunchResult fail(String m) {
            return new LaunchResult(false, m);
        }
    }
}

package com.aidesktopagent.coding;

import com.aidesktopagent.logging.LoggingService;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs allowlisted Maven/Gradle goals only — never arbitrary shell.
 */
public final class BuildRunner {

    public enum Tool {
        MAVEN, GRADLE, NONE
    }

    private static final List<String> ALLOWED_MAVEN = List.of(
            "compile", "test", "package", "clean", "verify", "dependency:tree");
    private static final List<String> ALLOWED_GRADLE = List.of(
            "build", "test", "clean", "assemble", "check");

    private final ProjectWorkspace workspace;
    private final LoggingService log;
    private final long timeoutSeconds;
    private final AtomicBoolean cancel = new AtomicBoolean(false);

    public BuildRunner(ProjectWorkspace workspace) {
        this(workspace, 300, LoggingService.forComponent("BuildRunner"));
    }

    public BuildRunner(ProjectWorkspace workspace, long timeoutSeconds, LoggingService log) {
        this.workspace = Objects.requireNonNull(workspace);
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 300;
        this.log = log != null ? log : LoggingService.forComponent("BuildRunner");
    }

    public void requestCancel() {
        cancel.set(true);
    }

    public BuildResult run(String goal) {
        cancel.set(false);
        Tool tool = detectTool();
        if (tool == Tool.NONE) {
            return BuildResult.fail("No Maven (pom.xml) or Gradle build file in workspace");
        }
        String g = goal == null || goal.isBlank() ? defaultGoal(tool) : goal.trim();
        if (!isAllowed(tool, g)) {
            return BuildResult.fail("Goal not allowlisted: " + g
                    + ". Allowed: " + (tool == Tool.MAVEN ? ALLOWED_MAVEN : ALLOWED_GRADLE));
        }

        List<String> cmd = new ArrayList<>();
        if (tool == Tool.MAVEN) {
            String mvn = findMaven();
            if (mvn == null) {
                return BuildResult.fail("mvn not found on PATH");
            }
            cmd.add(mvn);
            cmd.add("-q");
            cmd.add("-f");
            cmd.add(workspace.getRoot().resolve("pom.xml").toString());
            for (String part : g.split("\\s+")) {
                cmd.add(part);
            }
        } else {
            Path gradlew = workspace.getRoot().resolve("gradlew");
            if (Files.isExecutable(gradlew)) {
                cmd.add(gradlew.toString());
            } else {
                cmd.add("gradle");
            }
            cmd.add(g);
        }

        log.info("Build: " + String.join(" ", cmd));
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(workspace.getRoot().toFile());
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try (InputStream in = proc.getInputStream()) {
                byte[] chunk = new byte[4096];
                long deadline = System.currentTimeMillis() + timeoutSeconds * 1000;
                while (System.currentTimeMillis() < deadline) {
                    if (cancel.get()) {
                        proc.destroyForcibly();
                        return BuildResult.cancelled("Build cancelled");
                    }
                    if (in.available() > 0) {
                        int n = in.read(chunk);
                        if (n > 0) {
                            buf.write(chunk, 0, n);
                        }
                    } else if (!proc.isAlive()) {
                        break;
                    } else {
                        Thread.sleep(50);
                    }
                }
                if (proc.isAlive()) {
                    proc.destroyForcibly();
                    return BuildResult.fail("Build timed out after " + timeoutSeconds + "s\n" + truncate(buf.toString(StandardCharsets.UTF_8)));
                }
            }
            int code = proc.exitValue();
            String out = truncate(buf.toString(StandardCharsets.UTF_8));
            if (code == 0) {
                return BuildResult.ok("Build succeeded (" + g + ")", code, out);
            }
            return BuildResult.fail("Build failed exit=" + code + "\n" + summarizeFailures(out), code, out);
        } catch (Exception e) {
            return BuildResult.fail("Build error: " + e.getMessage());
        }
    }

    private Tool detectTool() {
        if (Files.isRegularFile(workspace.getRoot().resolve("pom.xml"))) {
            return Tool.MAVEN;
        }
        if (Files.isRegularFile(workspace.getRoot().resolve("build.gradle"))
                || Files.isRegularFile(workspace.getRoot().resolve("build.gradle.kts"))) {
            return Tool.GRADLE;
        }
        return Tool.NONE;
    }

    private static String defaultGoal(Tool tool) {
        return tool == Tool.MAVEN ? "test" : "test";
    }

        private static boolean isAllowed(Tool tool, String goal) {
        if (goal == null || goal.isBlank()) {
            return false;
        }
        // Exactly one goal token — no extra args via this entry point
        if (goal.chars().anyMatch(Character::isWhitespace)) {
            return false;
        }
        List<String> allowed = tool == Tool.MAVEN ? ALLOWED_MAVEN : ALLOWED_GRADLE;
        return allowed.contains(goal.trim());
    }

    private static String findMaven() {
        try {
            Process p = new ProcessBuilder("which", "mvn").start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            if (p.waitFor() == 0 && !out.isBlank()) {
                return out;
            }
        } catch (Exception ignored) {
        }
        Path bundled = Path.of("/tmp/apache-maven-3.9.9/bin/mvn");
        if (Files.isExecutable(bundled)) {
            return bundled.toString();
        }
        return null;
    }

    private static String summarizeFailures(String out) {
        if (out == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String line : out.split("\n")) {
            String l = line.toLowerCase();
            if (l.contains("error") || l.contains("failure") || l.contains("failed")) {
                sb.append(line).append('\n');
                if (sb.length() > 1500) {
                    break;
                }
            }
        }
        return sb.length() > 0 ? sb.toString() : truncate(out);
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 4000 ? s : s.substring(0, 4000) + "\n…";
    }

    public record BuildResult(boolean success, boolean cancelled, String message, int exitCode, String output) {
        static BuildResult ok(String m, int code, String out) {
            return new BuildResult(true, false, m, code, out);
        }

        static BuildResult fail(String m) {
            return new BuildResult(false, false, m, -1, "");
        }

        static BuildResult fail(String m, int code, String out) {
            return new BuildResult(false, false, m, code, out);
        }

        static BuildResult cancelled(String m) {
            return new BuildResult(false, true, m, -1, "");
        }
    }
}

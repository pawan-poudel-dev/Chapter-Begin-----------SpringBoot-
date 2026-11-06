package com.aidesktopagent.coding;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses Surefire / JUnit failure summaries (Day 23). */
public final class TestFailureDiagnosis {

    private static final Pattern FAILED = Pattern.compile(
            "(?:\\[ERROR\\]\\s+)?([\\w.]+)\\.([\\w]+)\\s+.*?Failures?:\\s*([1-9]\\d*)");
    private static final Pattern METHOD = Pattern.compile(
            "([\\w.]+)\\.([\\w]+):(\\d+)");

    public record FailedTest(String className, String method, String detail) {
    }

    public record Report(List<FailedTest> failures, String summary) {
        public String toHuman() {
            if (failures.isEmpty()) {
                return summary;
            }
            StringBuilder sb = new StringBuilder(summary).append('\n');
            for (FailedTest f : failures) {
                sb.append("  - ").append(f.className()).append('#').append(f.method());
                if (f.detail() != null && !f.detail().isBlank()) {
                    sb.append(" — ").append(f.detail());
                }
                sb.append('\n');
            }
            sb.append("Next: fix the first failure, re-run only that test class if possible.");
            return sb.toString().trim();
        }
    }

    public Report analyze(String output) {
        List<FailedTest> fails = new ArrayList<>();
        if (output == null || output.isBlank()) {
            return new Report(List.of(), "No test output.");
        }
        for (String line : output.split("\n")) {
            if (line.contains("<<< FAILURE") || line.contains("<<< ERROR")) {
                Matcher m = Pattern.compile("([\\w.]+)\\.([\\w]+)\\s").matcher(line);
                if (m.find()) {
                    fails.add(new FailedTest(m.group(1), m.group(2), line.trim()));
                }
            }
            if (line.contains("AssertionFailedError") || line.contains("expected:")) {
                fails.add(new FailedTest("unknown", "assert", line.trim()));
            }
        }
        // de-dup naive
        List<FailedTest> unique = new ArrayList<>();
        for (FailedTest f : fails) {
            if (unique.stream().noneMatch(u -> u.method().equals(f.method()) && u.className().equals(f.className()))) {
                unique.add(f);
            }
        }
        String summary = unique.isEmpty()
                ? (output.toLowerCase().contains("build success") ? "No test failures detected." : "No structured failures parsed.")
                : "Detected " + unique.size() + " failed test reference(s).";
        return new Report(List.copyOf(unique), summary);
    }
}

package com.aidesktopagent.coding;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses common javac / Maven compiler error lines (Day 22).
 */
public final class CompilerErrorDiagnosis {

    private static final Pattern JAVAC = Pattern.compile(
            "(.+\\.java):(\\d+):\\s*(?:error|warning):\\s*(.+)");
    private static final Pattern MAVEN = Pattern.compile(
            "\\[ERROR\\]\\s+(.+\\.java):\\[(\\d+),(\\d+)\\]\\s+(.+)");

    public record Diagnostic(String file, int line, int column, String message, boolean error) {
    }

    public record Report(List<Diagnostic> diagnostics, String explanation, String suggestion) {
        public String toHuman() {
            StringBuilder sb = new StringBuilder();
            sb.append(explanation).append('\n');
            for (Diagnostic d : diagnostics) {
                sb.append("  ").append(d.file()).append(':').append(d.line())
                        .append(" — ").append(d.message()).append('\n');
            }
            if (suggestion != null && !suggestion.isBlank()) {
                sb.append("Suggestion: ").append(suggestion);
            }
            return sb.toString().trim();
        }
    }

    public Report analyze(String buildOutput) {
        List<Diagnostic> list = new ArrayList<>();
        if (buildOutput == null || buildOutput.isBlank()) {
            return new Report(List.of(), "No compiler output provided.", "");
        }
        for (String line : buildOutput.split("\n")) {
            Matcher m = MAVEN.matcher(line.trim());
            if (m.find()) {
                list.add(new Diagnostic(m.group(1), Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3)), m.group(4), true));
                continue;
            }
            m = JAVAC.matcher(line.trim());
            if (m.find()) {
                list.add(new Diagnostic(m.group(1), Integer.parseInt(m.group(2)), 0, m.group(3), true));
            }
        }
        String explanation = list.isEmpty()
                ? "No structured compiler errors detected. Inspect the full log."
                : "Found " + list.size() + " compiler diagnostic(s).";
        String suggestion = "";
        if (!list.isEmpty()) {
            String msg = list.get(0).message().toLowerCase();
            if (msg.contains("cannot find symbol")) {
                suggestion = "Check imports, spelling, and that the dependency is on the classpath.";
            } else if (msg.contains("incompatible types")) {
                suggestion = "Align types or add an explicit cast only if safe.";
            } else if (msg.contains("method does not override")) {
                suggestion = "Verify method signature matches the parent/interface.";
            } else {
                suggestion = "Open the first file:line and fix before changing unrelated code.";
            }
        }
        return new Report(List.copyOf(list), explanation, suggestion);
    }
}

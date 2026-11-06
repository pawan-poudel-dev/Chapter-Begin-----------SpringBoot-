package com.aidesktopagent.coding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Explains common Java exceptions and stack traces in plain language.
 * Does not execute code or apply fixes — analysis only.
 */
public final class StackTraceAnalyzer {

    private static final Pattern EXCEPTION_LINE = Pattern.compile(
            "^([\\w.$]+(?:Error|Exception|Throwable))(?::\\s*(.*))?$");
    private static final Pattern FRAME = Pattern.compile(
            "^\\s*at\\s+([\\w.$]+)\\.([\\w$]+)\\(([^:]+)(?::(\\d+))?\\)");
    private static final Pattern CAUSED_BY = Pattern.compile(
            "^Caused by:\\s*([\\w.$]+)(?::\\s*(.*))?$");

    public AnalysisResult analyze(String stackTraceOrError) {
        if (stackTraceOrError == null || stackTraceOrError.isBlank()) {
            return AnalysisResult.empty("No error text provided.");
        }

        String[] lines = stackTraceOrError.replace("\r\n", "\n").split("\n");
        String primaryType = null;
        String primaryMessage = null;
        List<Frame> frames = new ArrayList<>();
        List<String> causes = new ArrayList<>();

        for (String raw : lines) {
            String line = raw.stripTrailing();
            String trimmed = line.trim();
            if (primaryType == null && !trimmed.startsWith("at ")) {
                String candidate = trimmed;
                if (candidate.startsWith("Exception in thread")) {
                    Matcher th = Pattern.compile(
                            "^Exception in thread\\s+\"[^\"]*\"\\s+(.*)$").matcher(candidate);
                    if (th.matches()) {
                        candidate = th.group(1).trim();
                    }
                }
                Matcher m = EXCEPTION_LINE.matcher(candidate);
                if (m.matches()) {
                    primaryType = m.group(1);
                    primaryMessage = m.group(2) != null ? m.group(2).trim() : "";
                    continue;
                }
            }
            Matcher c = CAUSED_BY.matcher(trimmed);
            if (c.matches()) {
                causes.add(c.group(1) + (c.group(2) != null ? ": " + c.group(2).trim() : ""));
                continue;
            }
            Matcher f = FRAME.matcher(line);
            if (f.find()) {
                frames.add(new Frame(f.group(1), f.group(2), f.group(3),
                        f.group(4) != null ? Integer.parseInt(f.group(4)) : -1));
            }
        }

        if (primaryType == null) {
            // Heuristic: first non-empty line
            for (String line : lines) {
                if (!line.isBlank()) {
                    primaryType = "UnknownError";
                    primaryMessage = line.trim();
                    break;
                }
            }
        }

        String explanation = explain(primaryType, primaryMessage);
        String suggestion = suggest(primaryType, primaryMessage, frames);
        Frame topUser = frames.stream()
                .filter(fr -> !fr.className.startsWith("java.")
                        && !fr.className.startsWith("jdk.")
                        && !fr.className.startsWith("sun.")
                        && !fr.className.startsWith("org.junit"))
                .findFirst()
                .orElse(frames.isEmpty() ? null : frames.get(0));

        return new AnalysisResult(primaryType, primaryMessage, explanation, suggestion,
                topUser, List.copyOf(frames), List.copyOf(causes));
    }

    private static String explain(String type, String message) {
        if (type == null) {
            return "Could not identify an exception type.";
        }
        String t = type.toLowerCase(Locale.ROOT);
        String msg = message != null ? message.toLowerCase(Locale.ROOT) : "";

        if (t.contains("nullpointer")) {
            return "A NullPointerException means something was null when the code tried to use it "
                    + "(call a method, read a field, or unbox). Find the first project frame and check "
                    + "which expression can be null.";
        }
        if (t.contains("classnotfound") || t.contains("noclassdeffound")) {
            return "A class the JVM expected is missing from the classpath. Often a missing dependency "
                    + "in pom.xml/build.gradle, or a wrong module path.";
        }
        if (t.contains("filenotfound") || (t.contains("ioexception") && msg.contains("no such file"))) {
            return "A file or path could not be found. Check working directory, relative paths, "
                    + "and that the resource exists on disk.";
        }
        if (t.contains("arrayindexoutofbounds") || t.contains("indexoutofbounds")) {
            return "Code accessed a list/array index that does not exist. Check loop bounds and size().";
        }
        if (t.contains("illegalargument")) {
            return "A method received an argument it does not accept. Validate inputs before the call.";
        }
        if (t.contains("illegalstate")) {
            return "An object was used in a state that is not allowed (e.g. closed stream, wrong lifecycle phase).";
        }
        if (t.contains("unsupportedoperation")) {
            return "An operation is not supported by this object (common with immutable collections).";
        }
        if (t.contains("stackoverflow")) {
            return "Unbounded recursion or a cycle in calls exhausted the stack. Look for recursive methods without a base case.";
        }
        if (t.contains("outofmemory")) {
            return "The JVM ran out of heap (or another memory area). Check large allocations, leaks, and -Xmx settings.";
        }
        if (t.contains("numberformat")) {
            return "A string could not be parsed as a number. Validate/format the input before parseInt/parseDouble.";
        }
        if (t.contains("sql")) {
            return "A database error occurred. Check connection, SQL syntax, and whether the DB is reachable.";
        }
        if (t.contains("interrupted")) {
            return "A thread was interrupted while waiting. Restore the interrupt flag and decide whether to abort.";
        }
        return "Exception type: " + type
                + (message != null && !message.isBlank() ? " — " + message : "")
                + ". Inspect the first frame in your own package for the failure site.";
    }

    private static String suggest(String type, String message, List<Frame> frames) {
        StringBuilder sb = new StringBuilder();
        if (type != null && type.toLowerCase(Locale.ROOT).contains("nullpointer")) {
            sb.append("Add null checks or Optional; enable Objects.requireNonNull at API boundaries. ");
        }
        Frame user = frames.stream()
                .filter(fr -> !fr.className.startsWith("java.") && !fr.className.startsWith("org.junit"))
                .findFirst()
                .orElse(null);
        if (user != null && user.line > 0) {
            sb.append("Start at ").append(user.className).append(".").append(user.method)
                    .append(" (").append(user.file).append(":").append(user.line).append("). ");
        }
        sb.append("Reproduce with a focused unit test before changing production code.");
        return sb.toString().trim();
    }

    public record Frame(String className, String method, String file, int line) {
        @Override
        public String toString() {
            return className + "." + method + "(" + file + (line > 0 ? ":" + line : "") + ")";
        }
    }

    public static final class AnalysisResult {
        private final String exceptionType;
        private final String message;
        private final String explanation;
        private final String suggestion;
        private final Frame primaryFrame;
        private final List<Frame> frames;
        private final List<String> causes;

        public AnalysisResult(String exceptionType, String message, String explanation,
                              String suggestion, Frame primaryFrame,
                              List<Frame> frames, List<String> causes) {
            this.exceptionType = exceptionType;
            this.message = message;
            this.explanation = explanation;
            this.suggestion = suggestion;
            this.primaryFrame = primaryFrame;
            this.frames = frames;
            this.causes = causes;
        }

        static AnalysisResult empty(String explanation) {
            return new AnalysisResult(null, null, explanation, "", null, List.of(), List.of());
        }

        public String getExceptionType() {
            return exceptionType;
        }

        public String getMessage() {
            return message;
        }

        public String getExplanation() {
            return explanation;
        }

        public String getSuggestion() {
            return suggestion;
        }

        public Frame getPrimaryFrame() {
            return primaryFrame;
        }

        public List<Frame> getFrames() {
            return frames;
        }

        public List<String> getCauses() {
            return causes;
        }

        public String toHumanReport() {
            StringBuilder sb = new StringBuilder();
            if (exceptionType != null) {
                sb.append(exceptionType);
                if (message != null && !message.isBlank()) {
                    sb.append(": ").append(message);
                }
                sb.append("\n");
            }
            sb.append(explanation).append("\n");
            if (primaryFrame != null) {
                sb.append("Likely site: ").append(primaryFrame).append("\n");
            }
            if (!causes.isEmpty()) {
                sb.append("Caused by: ").append(String.join(" → ", causes)).append("\n");
            }
            if (suggestion != null && !suggestion.isBlank()) {
                sb.append("Next step: ").append(suggestion);
            }
            return sb.toString().trim();
        }
    }
}

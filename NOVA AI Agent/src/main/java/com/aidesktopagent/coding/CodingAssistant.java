package com.aidesktopagent.coding;

import com.aidesktopagent.logging.LoggingService;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Coding assistant foundation (Day 6):
 * analyze project metadata, list/read sources inside a workspace,
 * explain stack traces, and produce <em>unapplied</em> change proposals.
 *
 * <p>Does not write files, run builds, or execute shell commands.
 */
public final class CodingAssistant {

    private final ProjectWorkspace workspace;
    private final ProjectMetadata metadata;
    private final SourceFileReader reader;
    private final StackTraceAnalyzer analyzer;
    private final PatchValidator patchValidator;
    private final LoggingService log;

    public CodingAssistant(Path projectRoot) {
        this(new ProjectWorkspace(projectRoot), LoggingService.forComponent("Coding"));
    }

    public CodingAssistant(ProjectWorkspace workspace, LoggingService log) {
        this.workspace = Objects.requireNonNull(workspace);
        this.metadata = ProjectMetadata.scan(workspace);
        this.reader = new SourceFileReader(workspace);
        this.analyzer = new StackTraceAnalyzer();
        this.patchValidator = new PatchValidator(workspace);
        this.log = log != null ? log : LoggingService.forComponent("Coding");
        log.info("Coding workspace: " + workspace.getRoot() + " (" + metadata.getBuildSystem() + ")");
    }

    public ProjectWorkspace getWorkspace() {
        return workspace;
    }

    public ProjectMetadata getMetadata() {
        return metadata;
    }

    public String analyzeProject() {
        return metadata.summary();
    }

    public List<String> listSources(String filter) throws IOException {
        return reader.listJavaSources(filter, SourceFileReader.DEFAULT_LIST_LIMIT);
    }

    public String readSource(String relativePath) throws IOException {
        log.info("Read source: " + relativePath);
        return reader.read(relativePath);
    }

    public StackTraceAnalyzer.AnalysisResult explainError(String stackTrace) {
        log.info("Analyze error text (" + (stackTrace != null ? stackTrace.length() : 0) + " chars)");
        return analyzer.analyze(stackTrace);
    }

    /**
     * Build a proposal to insert a simple null-check pattern or comment near a method.
     * Real LLM-driven patches come later; this demonstrates safe proposal + validation.
     */
    public CodeChangeProposal proposeSafeStub(String relativePath, String reason) {
        PatchValidator.ValidationOutcome v = patchValidator.validatePath(relativePath);
        if (!v.valid()) {
            return new CodeChangeProposal(relativePath, reason, "", "", false);
        }
        String original;
        try {
            original = reader.read(relativePath);
        } catch (IOException e) {
            return new CodeChangeProposal(relativePath, "Cannot read file: " + e.getMessage(),
                    "", "", true);
        }
        // Conservative proposal: add a package-level note as comment at top of class if possible
        String proposed = injectTodoComment(original, reason);
        return patchValidator.validateProposal(relativePath, reason, summarize(original), summarize(proposed));
    }

    /**
     * Validate an externally supplied proposal (e.g. from an LLM) without applying it.
     */
    public CodeChangeProposal validateExternalProposal(String relativePath, String description,
                                                       String original, String proposed) {
        return patchValidator.validateProposal(relativePath, description, original, proposed);
    }

    public String handleNaturalRequest(String command) {
        if (command == null || command.isBlank()) {
            return "Tell me what to analyze — project summary, a file path, or paste a stack trace.";
        }
        String lower = command.toLowerCase(Locale.ROOT);

        if (lower.contains("stack") || lower.contains("exception") || lower.contains("error:")
                || lower.contains("caused by")) {
            return explainError(command).toHumanReport();
        }
        if (lower.contains("analyze project") || lower.contains("project summary")
                || lower.equals("describe project") || lower.contains("what project")) {
            return analyzeProject();
        }
        if (lower.startsWith("list") && (lower.contains("source") || lower.contains("file") || lower.contains("class"))) {
            try {
                String filter = "";
                if (lower.contains("named ") || lower.contains("called ")) {
                    String[] parts = command.split("(?i)(?:named|called)\\s+");
                    if (parts.length > 1) {
                        filter = parts[1].replaceAll("[.!?]$", "").trim();
                    }
                }
                List<String> files = listSources(filter);
                if (files.isEmpty()) {
                    return "No Java sources found" + (filter.isEmpty() ? "." : " matching '" + filter + "'.");
                }
                StringBuilder sb = new StringBuilder("Found ").append(files.size()).append(" file(s):\n");
                for (String f : files) {
                    sb.append("  - ").append(f).append('\n');
                }
                return sb.toString().trim();
            } catch (IOException e) {
                return "Could not list sources: " + e.getMessage();
            }
        }
        if (lower.startsWith("read ") || lower.startsWith("show ") || lower.startsWith("open file ")) {
            String path = command.replaceFirst("(?i)^(read|show|open file)\\s+", "").trim();
            try {
                String content = readSource(path);
                if (content.length() > 4000) {
                    return content.substring(0, 4000) + "\n… (truncated)";
                }
                return content;
            } catch (IOException | SecurityException e) {
                return "Cannot read '" + path + "': " + e.getMessage();
            }
        }
        if (lower.contains("propose") || lower.contains("suggest fix") || lower.contains("suggest change")) {
            return "Day 6 produces validated change *proposals* only. "
                    + "Use proposeSafeStub(path, reason) from code, or wait for Day 8 safe editing. "
                    + "Paste a stack trace for analysis now.";
        }
        return analyzeProject() + "\n\nTry: \"analyze project\", \"list sources\", "
                + "\"read src/main/java/...\", or paste a stack trace.";
    }

    private static String injectTodoComment(String source, String reason) {
        String note = "// TODO [agent-proposal]: " + reason.replace("\n", " ") + "\n";
        int idx = source.indexOf("public class");
        if (idx < 0) {
            idx = source.indexOf("class ");
        }
        if (idx >= 0) {
            return source.substring(0, idx) + note + source.substring(idx);
        }
        return note + source;
    }

    private static String summarize(String s) {
        if (s == null) {
            return "";
        }
        if (s.length() <= 800) {
            return s;
        }
        return s.substring(0, 800) + "\n…";
    }
}

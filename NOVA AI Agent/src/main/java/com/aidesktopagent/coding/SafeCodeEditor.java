package com.aidesktopagent.coding;

import com.aidesktopagent.logging.LoggingService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Objects;

/**
 * Applies reviewable text replacements with backup files.
 * Requires caller confirmation for substantial changes (Day 8).
 */
public final class SafeCodeEditor {

    private final ProjectWorkspace workspace;
    private final PatchValidator validator;
    private final LoggingService log;
    private final Path backupDir;

    public SafeCodeEditor(ProjectWorkspace workspace) {
        this(workspace, LoggingService.forComponent("CodeEditor"));
    }

    public SafeCodeEditor(ProjectWorkspace workspace, LoggingService log) {
        this.workspace = Objects.requireNonNull(workspace);
        this.validator = new PatchValidator(workspace);
        this.log = log != null ? log : LoggingService.forComponent("CodeEditor");
        this.backupDir = workspace.getRoot().resolve(".agent-backups");
    }

    public DiffPreview previewReplace(String relativePath, String find, String replace) throws IOException {
        var v = validator.validatePath(relativePath);
        if (!v.valid()) {
            throw new SecurityException(v.reason());
        }
        Path file = workspace.resolveSafe(relativePath);
        if (!Files.isRegularFile(file)) {
            throw new IOException("Not a file: " + relativePath);
        }
        String original = Files.readString(file, StandardCharsets.UTF_8);
        if (!original.contains(find)) {
            return new DiffPreview(relativePath, original, original, false, "Pattern not found");
        }
        String updated = original.replace(find, replace);
        return new DiffPreview(relativePath, original, updated, true, null);
    }

    /**
     * Apply a full-file write after validation. Creates a timestamped backup first.
     */
    public ApplyResult applyFullContent(String relativePath, String newContent, boolean confirmed) throws IOException {
        if (!confirmed) {
            return ApplyResult.rejected("Confirmation required before writing files");
        }
        var v = validator.validatePath(relativePath);
        if (!v.valid()) {
            return ApplyResult.rejected(v.reason());
        }
        Path file = workspace.resolveSafe(relativePath);
        String previous = Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
        Path backup = backup(file, previous);
        Files.createDirectories(file.getParent());
        Files.writeString(file, newContent != null ? newContent : "", StandardCharsets.UTF_8);
        log.info("Applied edit to " + relativePath + " backup=" + backup);
        return ApplyResult.applied(relativePath, backup);
    }

    public ApplyResult applyReplace(String relativePath, String find, String replace, boolean confirmed)
            throws IOException {
        DiffPreview preview = previewReplace(relativePath, find, replace);
        if (!preview.canApply()) {
            return ApplyResult.rejected(preview.message());
        }
        return applyFullContent(relativePath, preview.updated(), confirmed);
    }

    public ApplyResult rollback(Path backupFile, String relativePath, boolean confirmed) throws IOException {
        if (!confirmed) {
            return ApplyResult.rejected("Confirmation required for rollback");
        }
        if (!Files.isRegularFile(backupFile)) {
            return ApplyResult.rejected("Backup not found: " + backupFile);
        }
        Path target = workspace.resolveSafe(relativePath);
        Files.copy(backupFile, target, StandardCopyOption.REPLACE_EXISTING);
        log.info("Rolled back " + relativePath + " from " + backupFile);
        return ApplyResult.applied(relativePath, backupFile);
    }

    private Path backup(Path file, String content) throws IOException {
        Files.createDirectories(backupDir);
        String name = file.getFileName() + "." + Instant.now().toEpochMilli() + ".bak";
        Path bak = backupDir.resolve(name);
        Files.writeString(bak, content != null ? content : "", StandardCharsets.UTF_8);
        return bak;
    }

    public record DiffPreview(String path, String original, String updated, boolean canApply, String message) {
        public String summary() {
            if (!canApply) {
                return "Cannot apply to " + path + ": " + message;
            }
            int origLines = original.split("\n", -1).length;
            int newLines = updated.split("\n", -1).length;
            return "Diff for " + path + ": " + origLines + " → " + newLines + " lines (preview only until confirmed)";
        }
    }

    public record ApplyResult(boolean success, String message, Path backup) {
        static ApplyResult applied(String path, Path backup) {
            return new ApplyResult(true, "Updated " + path, backup);
        }

        static ApplyResult rejected(String msg) {
            return new ApplyResult(false, msg, null);
        }
    }
}

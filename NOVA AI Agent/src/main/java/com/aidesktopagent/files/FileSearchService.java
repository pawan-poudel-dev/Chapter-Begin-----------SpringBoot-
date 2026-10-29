package com.aidesktopagent.files;

import com.aidesktopagent.coding.ProjectWorkspace;
import com.aidesktopagent.logging.LoggingService;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Search approved directories only (Day 13). Supports cancel and pagination.
 */
public final class FileSearchService {

    private final ProjectWorkspace workspace;
    private final LoggingService log;
    private final AtomicBoolean cancel = new AtomicBoolean(false);

    public FileSearchService(ProjectWorkspace workspace) {
        this(workspace, LoggingService.forComponent("FileSearch"));
    }

    public FileSearchService(ProjectWorkspace workspace, LoggingService log) {
        this.workspace = Objects.requireNonNull(workspace);
        this.log = log != null ? log : LoggingService.forComponent("FileSearch");
    }

    public void requestCancel() {
        cancel.set(true);
    }

    public SearchResult search(String nameContains, String extension, int limit) throws IOException {
        cancel.set(false);
        int lim = limit > 0 ? Math.min(limit, 500) : 50;
        final String name = nameContains != null ? nameContains.toLowerCase(Locale.ROOT) : "";
        String extRaw = extension != null ? extension.toLowerCase(Locale.ROOT) : "";
        if (extRaw.startsWith(".")) {
            extRaw = extRaw.substring(1);
        }
        final String ext = extRaw;

        List<String> hits = new ArrayList<>();
        Files.walkFileTree(workspace.getRoot(), new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (cancel.get()) {
                    return FileVisitResult.TERMINATE;
                }
                if (hits.size() >= lim) {
                    return FileVisitResult.TERMINATE;
                }
                String fn = file.getFileName().toString();
                String lower = fn.toLowerCase(Locale.ROOT);
                if (!name.isEmpty() && !lower.contains(name)) {
                    return FileVisitResult.CONTINUE;
                }
                if (!ext.isEmpty() && !lower.endsWith("." + ext)) {
                    return FileVisitResult.CONTINUE;
                }
                // Skip heavy/sensitive dirs
                String rel = workspace.getRoot().relativize(file).toString().replace('\\', '/');
                if (rel.contains("/.git/") || rel.contains("/node_modules/") || rel.contains("/target/")) {
                    return FileVisitResult.CONTINUE;
                }
                hits.add(rel);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (cancel.get()) {
                    return FileVisitResult.TERMINATE;
                }
                String n = dir.getFileName() != null ? dir.getFileName().toString() : "";
                if (n.equals(".git") || n.equals("node_modules") || n.equals("target") || n.equals(".agent-backups")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
        });

        log.info("File search name=" + name + " ext=" + ext + " hits=" + hits.size());
        return new SearchResult(hits, cancel.get(), lim);
    }

    public String summarizeFolder(String relative) throws IOException {
        Path dir = relative == null || relative.isBlank()
                ? workspace.getRoot()
                : workspace.resolveSafe(relative);
        if (!Files.isDirectory(dir)) {
            return "Not a directory: " + relative;
        }
        long files = 0, dirs = 0;
        try (var stream = Files.list(dir)) {
            for (Path p : stream.toList()) {
                if (Files.isDirectory(p)) {
                    dirs++;
                } else {
                    files++;
                }
            }
        }
        return "Folder " + workspace.getRoot().relativize(dir) + ": " + files + " file(s), " + dirs + " subfolder(s)";
    }

    public record SearchResult(List<String> paths, boolean cancelled, int limit) {
        public String summary() {
            if (paths.isEmpty()) {
                return cancelled ? "Search cancelled — no matches yet." : "No matches.";
            }
            StringBuilder sb = new StringBuilder("Found ").append(paths.size()).append(" match(es)");
            if (paths.size() >= limit) {
                sb.append(" (limit ").append(limit).append(")");
            }
            sb.append(":\n");
            for (String p : paths) {
                sb.append("  - ").append(p).append('\n');
            }
            return sb.toString().trim();
        }
    }
}

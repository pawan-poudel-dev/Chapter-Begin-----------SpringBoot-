package com.aidesktopagent.coding;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads source files only inside an approved {@link ProjectWorkspace}.
 */
public final class SourceFileReader {

    public static final int DEFAULT_MAX_BYTES = 256_000;
    public static final int DEFAULT_LIST_LIMIT = 200;

    private final ProjectWorkspace workspace;
    private final int maxBytes;

    public SourceFileReader(ProjectWorkspace workspace) {
        this(workspace, DEFAULT_MAX_BYTES);
    }

    public SourceFileReader(ProjectWorkspace workspace, int maxBytes) {
        this.workspace = workspace;
        this.maxBytes = maxBytes > 0 ? maxBytes : DEFAULT_MAX_BYTES;
    }

    public String read(String relativePath) throws IOException {
        Path path = workspace.resolveSafe(relativePath);
        if (!Files.isRegularFile(path)) {
            throw new IOException("Not a file: " + relativePath);
        }
        long size = Files.size(path);
        if (size > maxBytes) {
            throw new IOException("File too large (" + size + " bytes, max " + maxBytes + "): " + relativePath);
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /**
     * List Java sources under the workspace, optionally filtered by simple name contains.
     */
    public List<String> listJavaSources(String nameFilter, int limit) throws IOException {
        int lim = limit > 0 ? Math.min(limit, DEFAULT_LIST_LIMIT) : DEFAULT_LIST_LIMIT;
        String filter = nameFilter != null ? nameFilter.toLowerCase(Locale.ROOT) : "";
        List<String> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(workspace.getRoot())) {
            List<Path> files = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> workspace.contains(p))
                    .filter(p -> filter.isEmpty()
                            || p.getFileName().toString().toLowerCase(Locale.ROOT).contains(filter))
                    .limit(lim)
                    .collect(Collectors.toList());
            for (Path p : files) {
                out.add(workspace.getRoot().relativize(p).toString().replace('\\', '/'));
            }
        }
        return out;
    }
}

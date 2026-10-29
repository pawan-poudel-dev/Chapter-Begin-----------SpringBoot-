package com.aidesktopagent.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * Central filesystem authorization: every file op must stay inside an approved workspace.
 * Rejects absolute paths, traversal, and symlink escapes (best-effort).
 */
public final class SafePathResolver {

    private final Path root;

    public SafePathResolver(Path root) throws IOException {
        if (root == null) {
            throw new IllegalArgumentException("Workspace root is required");
        }
        Path abs = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(abs)) {
            throw new IllegalArgumentException("Workspace root must be a directory: " + abs);
        }
        // Canonicalize when possible (existing path)
        this.root = Files.exists(abs) ? abs.toRealPath() : abs;
    }

    public Path getRoot() {
        return root;
    }

    /**
     * Resolve a user/AI-supplied path relative to the workspace.
     *
     * @throws SecurityException if the path escapes the workspace
     */
    public Path resolve(String input) throws IOException {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Path is required");
        }
        if (input.contains("\0")) {
            throw new SecurityException("Null byte in path");
        }

        Path supplied = Path.of(input.trim());
        if (supplied.isAbsolute()) {
            throw new SecurityException("Absolute paths are not permitted; use a path relative to the workspace");
        }

        Path candidate = root.resolve(supplied).normalize();
        if (!candidate.startsWith(root)) {
            throw new SecurityException("Path escapes the approved workspace: " + input);
        }

        // Walk up to an existing ancestor and ensure real path stays under root (symlink escape)
        Path existing = candidate;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            existing = existing.getParent();
        }
        if (existing != null) {
            Path real = existing.toRealPath();
            if (!real.startsWith(root) && !real.equals(root)) {
                throw new SecurityException("Path resolves outside the workspace (symlink or mount)");
            }
        }

        // If the leaf exists as a symlink, verify its real path
        if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(candidate)) {
                Path real = candidate.toRealPath();
                if (!real.startsWith(root) && !real.equals(root)) {
                    throw new SecurityException("Symlink target is outside the workspace");
                }
            } else if (Files.exists(candidate)) {
                Path real = candidate.toRealPath(LinkOption.NOFOLLOW_LINKS);
                // For non-links, still verify parent real path
                Path parent = candidate.getParent();
                if (parent != null && Files.exists(parent)) {
                    Path parentReal = parent.toRealPath();
                    if (!parentReal.startsWith(root) && !parentReal.equals(root)) {
                        throw new SecurityException("Parent path is outside the workspace");
                    }
                }
            }
        }

        return candidate;
    }

    /** Validate without requiring the path to exist yet (for create). */
    public Path resolveForCreate(String input) throws IOException {
        return resolve(input);
    }
}

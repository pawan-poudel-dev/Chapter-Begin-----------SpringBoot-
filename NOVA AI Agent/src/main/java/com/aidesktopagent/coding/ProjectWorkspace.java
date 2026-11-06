package com.aidesktopagent.coding;

import com.aidesktopagent.security.SafePathResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Bounds operations to an approved root using SafePathResolver (real-path aware). */
public final class ProjectWorkspace {

    private final Path root;
    private final SafePathResolver resolver;

    public ProjectWorkspace(Path root) {
        Objects.requireNonNull(root, "root");
        try {
            this.resolver = new SafePathResolver(root);
            this.root = resolver.getRoot();
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid workspace root: " + root + " — " + e.getMessage(), e);
        }
        if (!Files.isDirectory(this.root)) {
            throw new IllegalArgumentException("Workspace root is not a directory: " + this.root);
        }
    }

    public Path getRoot() {
        return root;
    }

    public SafePathResolver getResolver() {
        return resolver;
    }

    public Path resolveSafe(String relativeOrAbsolute) {
        try {
            return resolver.resolve(relativeOrAbsolute);
        } catch (IOException e) {
            throw new SecurityException("Cannot resolve path: " + e.getMessage(), e);
        }
    }

    public boolean contains(Path path) {
        if (path == null) {
            return false;
        }
        try {
            Path n = path.toAbsolutePath().normalize();
            if (!n.startsWith(root)) {
                return false;
            }
            if (Files.exists(n)) {
                Path real = n.toRealPath();
                return real.startsWith(root) || real.equals(root);
            }
            Path parent = n.getParent();
            while (parent != null && !Files.exists(parent)) {
                parent = parent.getParent();
            }
            if (parent == null) {
                return false;
            }
            Path realParent = parent.toRealPath();
            return realParent.startsWith(root) || realParent.equals(root);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return "ProjectWorkspace{root=" + root + "}";
    }
}

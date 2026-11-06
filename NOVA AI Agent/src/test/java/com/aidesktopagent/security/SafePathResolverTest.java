package com.aidesktopagent.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SafePathResolverTest {

    @TempDir
    Path temp;
    private SafePathResolver resolver;

    @BeforeEach
    void setUp() throws Exception {
        resolver = new SafePathResolver(temp);
    }

    @Test
    void allowsRelative() throws Exception {
        Path p = resolver.resolve("src/Main.java");
        assertTrue(p.startsWith(resolver.getRoot()));
    }

    @Test
    void rejectsDotDot() {
        assertThrows(SecurityException.class, () -> resolver.resolve("../../etc/passwd"));
    }

    @Test
    void rejectsAbsolute() {
        assertThrows(SecurityException.class, () -> resolver.resolve("/etc/passwd"));
    }

    @Test
    void rejectsSymlinkEscape() throws Exception {
        Path outside = Files.createTempDirectory("outside-ws");
        try {
            Path link = temp.resolve("escape-link");
            try {
                Files.createSymbolicLink(link, outside);
            } catch (UnsupportedOperationException | java.nio.file.FileSystemException e) {
                return; // environment may disallow symlinks
            }
            assertThrows(SecurityException.class, () -> resolver.resolve("escape-link/secret.txt"));
        } finally {
            // cleanup best-effort
            try { Files.deleteIfExists(outside); } catch (Exception ignored) {}
        }
    }
}

package com.aidesktopagent.coding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProjectWorkspaceTest {

    @TempDir
    Path temp;

    @Test
    void resolvesRelativeInside() throws Exception {
        Path f = temp.resolve("src/Main.java");
        Files.createDirectories(f.getParent());
        Files.writeString(f, "class Main {}");
        ProjectWorkspace ws = new ProjectWorkspace(temp);
        Path resolved = ws.resolveSafe("src/Main.java");
        assertTrue(ws.contains(resolved));
        assertTrue(Files.isRegularFile(resolved));
    }

    @Test
    void rejectsTraversal() {
        ProjectWorkspace ws = new ProjectWorkspace(temp);
        assertThrows(SecurityException.class, () -> ws.resolveSafe("../../etc/passwd"));
    }

    @Test
    void rejectsOutsideAbsolute() throws Exception {
        ProjectWorkspace ws = new ProjectWorkspace(temp);
        Path outside = Files.createTempFile("outside", ".txt");
        try {
            assertThrows(SecurityException.class, () -> ws.resolveSafe(outside.toString()));
        } finally {
            Files.deleteIfExists(outside);
        }
    }
}

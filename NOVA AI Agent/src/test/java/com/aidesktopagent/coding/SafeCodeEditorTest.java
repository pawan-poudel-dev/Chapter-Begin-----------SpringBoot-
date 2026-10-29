package com.aidesktopagent.coding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SafeCodeEditorTest {

    @TempDir
    Path temp;
    private SafeCodeEditor editor;

    @BeforeEach
    void setUp() throws Exception {
        Files.writeString(temp.resolve("A.java"), "public class A { int x = 1; }\n");
        editor = new SafeCodeEditor(new ProjectWorkspace(temp));
    }

    @Test
    void previewReplace() throws Exception {
        var p = editor.previewReplace("A.java", "x = 1", "x = 2");
        assertTrue(p.canApply());
        assertTrue(p.updated().contains("x = 2"));
    }

    @Test
    void applyRequiresConfirmation() throws Exception {
        var r = editor.applyReplace("A.java", "x = 1", "x = 2", false);
        assertFalse(r.success());
        assertTrue(Files.readString(temp.resolve("A.java")).contains("x = 1"));
    }

    @Test
    void applyWithConfirmAndBackup() throws Exception {
        var r = editor.applyReplace("A.java", "x = 1", "x = 2", true);
        assertTrue(r.success());
        assertTrue(Files.readString(temp.resolve("A.java")).contains("x = 2"));
        assertNotNull(r.backup());
        assertTrue(Files.isRegularFile(r.backup()));
    }

    @Test
    void rejectsTraversal() {
        assertThrows(SecurityException.class,
                () -> editor.previewReplace("../../etc/passwd", "a", "b"));
    }
}

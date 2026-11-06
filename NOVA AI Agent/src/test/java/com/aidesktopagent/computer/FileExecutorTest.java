package com.aidesktopagent.computer;

import com.aidesktopagent.actions.Action;
import com.aidesktopagent.actions.ActionResult;
import com.aidesktopagent.actions.ActionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.aidesktopagent.security.SafePathResolver;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileExecutorTest {

    @TempDir
    Path temp;

    private FileExecutor executor;

    @BeforeEach
    void setUp() throws Exception {
        executor = new FileExecutor(null, new SafePathResolver(temp));
    }

    @Test
    void createFolder() throws Exception {
        Action a = Action.builder(ActionType.CREATE_FOLDER).target("AI Projects").build();
        ActionResult r = executor.execute(a);
        assertTrue(r.isSuccess());
        assertTrue(Files.isDirectory(temp.resolve("AI Projects")));
    }

    @Test
    void createFile() throws Exception {
        Action a = Action.builder(ActionType.CREATE_FILE).target("test.txt").build();
        ActionResult r = executor.execute(a);
        assertTrue(r.isSuccess());
        assertTrue(Files.isRegularFile(temp.resolve("test.txt")));
    }

    @Test
    void readFile() throws Exception {
        Files.writeString(temp.resolve("hello.txt"), "hello");
        Action a = Action.builder(ActionType.READ_FILE).target("hello.txt").build();
        ActionResult r = executor.execute(a);
        assertTrue(r.isSuccess());
        assertEquals("hello", r.getDetail());
    }

    @Test
    void copyFile() throws Exception {
        Files.writeString(temp.resolve("a.txt"), "data");
        Action a = Action.builder(ActionType.COPY_FILE)
                .target("a.txt")
                .parameter("destination", "b.txt")
                .build();
        ActionResult r = executor.execute(a);
        assertTrue(r.isSuccess());
        assertTrue(Files.exists(temp.resolve("b.txt")));
        assertEquals("data", Files.readString(temp.resolve("b.txt")));
    }

    @Test
    void deleteFile() throws Exception {
        Files.writeString(temp.resolve("gone.txt"), "x");
        Action a = Action.builder(ActionType.DELETE_FILE).target("gone.txt").build();
        ActionResult r = executor.execute(a);
        assertTrue(r.isSuccess());
        assertFalse(Files.exists(temp.resolve("gone.txt")));
    }

    @Test
    void deleteNonEmptyDirRefused() throws Exception {
        Path dir = temp.resolve("full");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("f.txt"), "x");
        Action a = Action.builder(ActionType.DELETE_FILE).target("full").build();
        ActionResult r = executor.execute(a);
        assertFalse(r.isSuccess());
        assertTrue(Files.exists(dir));
    }

    @Test
    void rejectsTraversal() {
        Action a = Action.builder(ActionType.READ_FILE).target("../../etc/passwd").build();
        ActionResult r = executor.execute(a);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().toLowerCase().contains("escap")
                || r.getMessage().toLowerCase().contains("traversal")
                || r.getMessage().toLowerCase().contains("not permitted")
                || r.getMessage().toLowerCase().contains("workspace"));
    }

    @Test
    void rejectsAbsolutePath() {
        Action a = Action.builder(ActionType.READ_FILE).target("/etc/passwd").build();
        ActionResult r = executor.execute(a);
        assertFalse(r.isSuccess());
    }

    @Test
    void refusesOverwriteWithoutApproval() throws Exception {
        Files.writeString(temp.resolve("a.txt"), "1");
        Files.writeString(temp.resolve("b.txt"), "2");
        Action a = Action.builder(ActionType.COPY_FILE)
                .target("a.txt")
                .parameter("destination", "b.txt")
                .build();
        ActionResult r = executor.execute(a);
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().toLowerCase().contains("overwrite"));
        assertEquals("2", Files.readString(temp.resolve("b.txt")));
    }
}

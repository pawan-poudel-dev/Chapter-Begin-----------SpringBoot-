package com.aidesktopagent.coding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BuildRunnerAllowlistTest {

    @TempDir
    Path temp;

    @Test
    void rejectsUnknownGoal() throws Exception {
        Files.writeString(temp.resolve("pom.xml"), "<project></project>");
        BuildRunner runner = new BuildRunner(new ProjectWorkspace(temp));
        var r = runner.run("compileEvil");
        assertFalse(r.success());
        assertTrue(r.message().toLowerCase().contains("allowed")
                || r.message().toLowerCase().contains("not allowed")
                || r.message().toLowerCase().contains("goal"));
    }

    @Test
    void rejectsMultiTokenGoal() throws Exception {
        Files.writeString(temp.resolve("pom.xml"), "<project></project>");
        BuildRunner runner = new BuildRunner(new ProjectWorkspace(temp));
        var r = runner.run("test -DskipTests");
        assertFalse(r.success());
    }
}

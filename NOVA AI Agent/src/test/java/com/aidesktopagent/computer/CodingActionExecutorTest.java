package com.aidesktopagent.computer;

import com.aidesktopagent.actions.Action;
import com.aidesktopagent.actions.ActionResult;
import com.aidesktopagent.actions.ActionType;
import com.aidesktopagent.coding.CodingAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CodingActionExecutorTest {

    @TempDir
    Path temp;

    private CodingActionExecutor executor;

    @BeforeEach
    void setUp() throws Exception {
        Files.writeString(temp.resolve("pom.xml"), "<project><artifactId>t</artifactId></project>");
        Path src = temp.resolve("src/main/java/A.java");
        Files.createDirectories(src.getParent());
        Files.writeString(src, "public class A {}");
        executor = new CodingActionExecutor(new CodingAssistant(temp));
    }

    @Test
    void analyzeProject() {
        ActionResult r = executor.execute(Action.builder(ActionType.ANALYZE_PROJECT).build());
        assertTrue(r.isSuccess());
        assertTrue(r.getMessage().contains("MAVEN") || r.getMessage().toLowerCase().contains("project"));
    }

    @Test
    void explainError() {
        Action a = Action.builder(ActionType.EXPLAIN_ERROR)
                .target("java.lang.NullPointerException: x\n\tat com.A.m(A.java:1)")
                .build();
        ActionResult r = executor.execute(a);
        assertTrue(r.isSuccess());
        assertTrue(r.getMessage().toLowerCase().contains("null"));
    }

    @Test
    void proposeKeepsFilesUntouched() throws Exception {
        String before = Files.readString(temp.resolve("src/main/java/A.java"));
        Action a = Action.builder(ActionType.PROPOSE_CODE_CHANGE)
                .target("src/main/java/A.java")
                .parameter("reason", "demo")
                .build();
        ActionResult r = executor.execute(a);
        assertTrue(r.isSuccess());
        assertEquals(before, Files.readString(temp.resolve("src/main/java/A.java")));
        assertTrue(r.getMessage().contains("Not applied"));
    }
}

package com.aidesktopagent.coding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CodingAssistantTest {

    @TempDir
    Path temp;

    private CodingAssistant assistant;

    @BeforeEach
    void setUp() throws Exception {
        Files.writeString(temp.resolve("pom.xml"),
                "<project><artifactId>demo-app</artifactId></project>");
        Path src = temp.resolve("src/main/java/com/demo/Hello.java");
        Files.createDirectories(src.getParent());
        Files.writeString(src, "package com.demo;\npublic class Hello {\n  public void run() {}\n}\n");
        assistant = new CodingAssistant(temp);
    }

    @Test
    void analyzesMavenProject() {
        String s = assistant.analyzeProject();
        assertTrue(s.contains("MAVEN") || s.contains("demo"));
        assertEquals(ProjectMetadata.BuildSystem.MAVEN, assistant.getMetadata().getBuildSystem());
    }

    @Test
    void listsSources() throws Exception {
        List<String> files = assistant.listSources("Hello");
        assertFalse(files.isEmpty());
        assertTrue(files.get(0).contains("Hello.java"));
    }

    @Test
    void readsSource() throws Exception {
        String content = assistant.readSource("src/main/java/com/demo/Hello.java");
        assertTrue(content.contains("class Hello"));
    }

    @Test
    void blocksOutsideRead() {
        assertThrows(SecurityException.class, () -> assistant.readSource("../../etc/passwd"));
    }

    @Test
    void proposalDoesNotApply() {
        CodeChangeProposal p = assistant.proposeSafeStub(
                "src/main/java/com/demo/Hello.java", "Add logging");
        assertTrue(p.isValidated());
        assertTrue(p.toPreview().contains("Not applied"));
    }

    @Test
    void naturalAnalyze() {
        String r = assistant.handleNaturalRequest("analyze project");
        assertTrue(r.toLowerCase().contains("project") || r.contains("MAVEN"));
    }
}

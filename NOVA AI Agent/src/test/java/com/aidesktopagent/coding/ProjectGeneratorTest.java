package com.aidesktopagent.coding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProjectGeneratorTest {

    @TempDir
    Path temp;

    @Test
    void generatesMavenLayout() throws Exception {
        Path root = new ProjectGenerator(temp).generate("sample-app", "com.example.sample");
        assertTrue(Files.isRegularFile(root.resolve("pom.xml")));
        assertTrue(Files.isRegularFile(root.resolve("src/main/java/com/example/sample/SampleApp.java")));
        assertTrue(Files.isRegularFile(root.resolve("src/test/java/com/example/sample/SampleAppTest.java")));
    }

    @Test
    void rejectsBadNames() {
        ProjectGenerator g = new ProjectGenerator(temp);
        assertThrows(IllegalArgumentException.class, () -> g.generate("../evil", "com.x"));
        assertThrows(IllegalArgumentException.class, () -> g.generate("ok", "Bad.Package"));
    }
}

package com.aidesktopagent.files;

import com.aidesktopagent.coding.ProjectWorkspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileSearchServiceTest {

    @TempDir
    Path temp;

    @Test
    void findsByName() throws Exception {
        Path f = temp.resolve("src/HelloWorld.java");
        Files.createDirectories(f.getParent());
        Files.writeString(f, "class HelloWorld {}");
        FileSearchService s = new FileSearchService(new ProjectWorkspace(temp));
        var r = s.search("Hello", "java", 10);
        assertFalse(r.paths().isEmpty());
        assertTrue(r.summary().contains("Hello"));
    }

    @Test
    void summarize() throws Exception {
        Files.writeString(temp.resolve("a.txt"), "x");
        FileSearchService s = new FileSearchService(new ProjectWorkspace(temp));
        assertTrue(s.summarizeFolder("").contains("file"));
    }
}

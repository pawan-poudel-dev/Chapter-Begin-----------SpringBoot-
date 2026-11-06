package com.aidesktopagent.coding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProjectIndexTest {

    @TempDir
    Path temp;

    @Test
    void indexesClasses() throws Exception {
        Path f = temp.resolve("src/main/java/com/demo/Hello.java");
        Files.createDirectories(f.getParent());
        Files.writeString(f, "package com.demo;\npublic class Hello {}\n");
        ProjectIndex idx = new ProjectIndex(new ProjectWorkspace(temp));
        idx.rebuild();
        assertFalse(idx.allTypes().isEmpty());
        assertTrue(idx.findBySimpleName("Hello").isPresent());
        assertTrue(idx.summary().contains("types"));
    }
}

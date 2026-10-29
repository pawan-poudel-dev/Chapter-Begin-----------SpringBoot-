package com.aidesktopagent.automation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowRepositoryTest {

    @TempDir
    Path temp;

    @Test
    void saveLoad() {
        Path f = temp.resolve("wf.json");
        WorkflowRepository repo = new WorkflowRepository(f);
        repo.save(new NamedWorkflow("morning", List.of("Open Chrome", "Open YouTube"), "start day"));
        WorkflowRepository repo2 = new WorkflowRepository(f);
        assertTrue(repo2.get("morning").isPresent());
        assertEquals(2, repo2.get("morning").orElseThrow().getSteps().size());
    }
}

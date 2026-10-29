package com.aidesktopagent.coding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PatchValidatorTest {

    @TempDir
    Path temp;

    private PatchValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PatchValidator(new ProjectWorkspace(temp));
    }

    @Test
    void acceptsRelative() {
        assertTrue(validator.validatePath("src/main/java/Foo.java").valid());
    }

    @Test
    void rejectsTraversal() {
        assertFalse(validator.validatePath("../../../etc/passwd").valid());
    }

    @Test
    void proposalMarkedInvalidOutside() {
        CodeChangeProposal p = validator.validateProposal("../../x.java", "bad", "a", "b");
        assertFalse(p.isValidated());
    }

    @Test
    void proposalValidInside() {
        CodeChangeProposal p = validator.validateProposal("src/A.java", "ok", "old", "new");
        assertTrue(p.isValidated());
        assertTrue(p.toPreview().contains("path OK"));
    }
}

package com.aidesktopagent.actions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PathSafetyValidatorTest {

    @Test
    void rejectsAbsoluteAndTraversal() {
        ActionValidator v = new ActionValidator();
        var abs = Action.builder(ActionType.READ_FILE).target("/etc/passwd").build();
        assertNotNull(v.validateOne(abs));
        var trav = Action.builder(ActionType.DELETE_FILE).target("../../etc/passwd").build();
        assertNotNull(v.validateOne(trav));
        var ok = Action.builder(ActionType.CREATE_FILE).target("notes/todo.txt").build();
        assertNull(v.validateOne(ok));
        // Filename containing ".." as characters is fine; path component ".." is not
        var dotted = Action.builder(ActionType.CREATE_FILE).target("report..old.txt").build();
        assertNull(v.validateOne(dotted));
    }
}

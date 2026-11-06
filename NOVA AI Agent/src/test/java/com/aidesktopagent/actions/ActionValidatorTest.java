package com.aidesktopagent.actions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ActionValidatorTest {

    private ActionValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ActionValidator();
    }

    @Test
    void acceptsValidOpenApplication() {
        Action a = Action.builder(ActionType.OPEN_APPLICATION).target("Chrome").build();
        ActionPlan plan = ActionPlan.of("Open Chrome", a);
        var result = validator.validate(plan);
        assertTrue(result.hasAcceptedActions());
        assertFalse(result.hasRejections());
        assertEquals(1, result.getPlan().size());
    }

    @Test
    void rejectsOpenApplicationWithoutName() {
        Action a = Action.builder(ActionType.OPEN_APPLICATION).build();
        var result = validator.validate(ActionPlan.of("open", a));
        assertFalse(result.hasAcceptedActions());
        assertTrue(result.hasRejections());
    }

    @Test
    void rejectsPathTraversal() {
        Action a = Action.builder(ActionType.DELETE_FILE).target("../../etc/passwd").build();
        var result = validator.validate(ActionPlan.of("delete", a));
        assertFalse(result.hasAcceptedActions());
    }

    @Test
    void rejectsDisallowedRunCommand() {
        Action a = Action.builder(ActionType.RUN_COMMAND).target("rm -rf /").build();
        var result = validator.validate(ActionPlan.of("run", a));
        assertFalse(result.hasAcceptedActions());
    }

    @Test
    void rejectsShellMetacharacters() {
        Action a = Action.builder(ActionType.RUN_COMMAND).target("ls | cat").build();
        var result = validator.validate(ActionPlan.of("run", a));
        assertFalse(result.hasAcceptedActions());
    }

    @Test
    void acceptsAllowedRunCommand() {
        Action a = Action.builder(ActionType.RUN_COMMAND).target("date").build();
        var result = validator.validate(ActionPlan.of("run date", a));
        assertTrue(result.hasAcceptedActions());
    }

    @Test
    void rejectsNonHttpUrl() {
        Action a = Action.builder(ActionType.OPEN_WEBSITE).target("file:///etc/passwd").build();
        var result = validator.validate(ActionPlan.of("open", a));
        assertFalse(result.hasAcceptedActions());
    }

    @Test
    void acceptsHttpsUrl() {
        Action a = Action.builder(ActionType.OPEN_WEBSITE).target("https://example.com").build();
        var result = validator.validate(ActionPlan.of("open", a));
        assertTrue(result.hasAcceptedActions());
    }

    @Test
    void acceptsBareDomainAsWebsite() {
        Action a = Action.builder(ActionType.OPEN_WEBSITE).target("example.com").build();
        var result = validator.validate(ActionPlan.of("open", a));
        assertTrue(result.hasAcceptedActions());
    }

    @Test
    void copyRequiresDestination() {
        Action a = Action.builder(ActionType.COPY_FILE).target("a.txt").build();
        var result = validator.validate(ActionPlan.of("copy", a));
        assertFalse(result.hasAcceptedActions());
    }

    @Test
    void destructiveFlagOnDelete() {
        Action a = Action.builder(ActionType.DELETE_FILE).target("x.txt").build();
        assertTrue(a.isDestructive());
    }
}

package com.aidesktopagent.actions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ActionParserTest {

    private ActionParser parser;

    @BeforeEach
    void setUp() {
        parser = new ActionParser();
    }

    @Test
    void parsesSimpleOpenApplication() throws Exception {
        String json = """
                {
                  "actions": [
                    { "type": "OPEN_APPLICATION", "target": "Chrome" }
                  ]
                }
                """;
        ActionPlan plan = parser.parse("Open Chrome", json);
        assertEquals(1, plan.size());
        Action a = plan.getActions().get(0);
        assertEquals(ActionType.OPEN_APPLICATION, a.getType());
        assertEquals("Chrome", a.getTarget().orElseThrow());
    }

    @Test
    void parsesMultipleActions() throws Exception {
        String json = """
                {
                  "actions": [
                    { "type": "CREATE_FOLDER", "target": "AI Projects" },
                    { "type": "CREATE_FILE", "target": "AI Projects/notes.txt" }
                  ],
                  "rationale": "create workspace"
                }
                """;
        ActionPlan plan = parser.parse("create workspace", json);
        assertEquals(2, plan.size());
        assertEquals("create workspace", plan.getRationale());
    }

    @Test
    void stripsMarkdownFences() throws Exception {
        String json = """
                ```json
                {
                  "actions": [
                    { "type": "OPEN_WEBSITE", "target": "https://youtube.com" }
                  ]
                }
                ```
                """;
        ActionPlan plan = parser.parse("Open YouTube", json);
        assertEquals(1, plan.size());
        assertEquals(ActionType.OPEN_WEBSITE, plan.getActions().get(0).getType());
    }

    @Test
    void skipsUnknownActionTypes() throws Exception {
        String json = """
                {
                  "actions": [
                    { "type": "HACK_THE_PLANET", "target": "x" },
                    { "type": "OPEN_APPLICATION", "target": "Firefox" }
                  ]
                }
                """;
        ActionPlan plan = parser.parse("test", json);
        assertEquals(1, plan.size());
        assertEquals(ActionType.OPEN_APPLICATION, plan.getActions().get(0).getType());
    }

    @Test
    void rejectsMissingActionsArray() {
        assertThrows(ActionParseException.class, () ->
                parser.parse("x", "{ \"foo\": 1 }"));
    }

    @Test
    void rejectsEmptyResponse() {
        assertThrows(ActionParseException.class, () ->
                parser.parse("x", "   "));
    }

    @Test
    void acceptsAlternateFieldNames() throws Exception {
        String json = """
                {
                  "actions": [
                    { "type": "OPEN_WEBSITE", "url": "https://example.com" }
                  ]
                }
                """;
        ActionPlan plan = parser.parse("go to example", json);
        assertEquals("https://example.com", plan.getActions().get(0).getTarget().orElseThrow());
    }
}

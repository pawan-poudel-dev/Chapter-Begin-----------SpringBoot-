package com.aidesktopagent.ai;

import com.aidesktopagent.actions.ActionParser;
import com.aidesktopagent.actions.ActionPlan;
import com.aidesktopagent.actions.ActionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RuleBasedAIClientTest {

    private RuleBasedAIClient client;
    private ActionParser parser;

    @BeforeEach
    void setUp() {
        client = new RuleBasedAIClient();
        parser = new ActionParser();
    }

    @Test
    void openChrome() throws Exception {
        String json = client.planActions("Open Chrome");
        ActionPlan plan = parser.parse("Open Chrome", json);
        assertEquals(1, plan.size());
        assertEquals(ActionType.OPEN_APPLICATION, plan.getActions().get(0).getType());
        assertTrue(plan.getActions().get(0).getTarget().orElse("").toLowerCase().contains("chrome"));
    }

    @Test
    void createFolder() throws Exception {
        String json = client.planActions("Create a folder called AI Projects");
        ActionPlan plan = parser.parse("cmd", json);
        assertEquals(ActionType.CREATE_FOLDER, plan.getActions().get(0).getType());
        assertEquals("AI Projects", plan.getActions().get(0).getTarget().orElseThrow());
    }

    @Test
    void createFile() throws Exception {
        String json = client.planActions("Create a file called test.txt");
        ActionPlan plan = parser.parse("cmd", json);
        assertEquals(ActionType.CREATE_FILE, plan.getActions().get(0).getType());
        assertEquals("test.txt", plan.getActions().get(0).getTarget().orElseThrow());
    }

    @Test
    void openYoutube() throws Exception {
        String json = client.planActions("Open YouTube");
        ActionPlan plan = parser.parse("cmd", json);
        assertEquals(ActionType.OPEN_WEBSITE, plan.getActions().get(0).getType());
    }

    @Test
    void typeText() throws Exception {
        String json = client.planActions("Type Hello World");
        ActionPlan plan = parser.parse("cmd", json);
        assertEquals(ActionType.TYPE_TEXT, plan.getActions().get(0).getType());
        assertEquals("Hello World", plan.getActions().get(0).getTarget().orElseThrow());
    }

    @Test
    void emptyCommandThrows() {
        assertThrows(AIClientException.class, () -> client.planActions("  "));
    }
}

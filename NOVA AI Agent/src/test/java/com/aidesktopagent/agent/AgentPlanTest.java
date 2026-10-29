package com.aidesktopagent.agent;

import com.aidesktopagent.actions.ActionParser;
import com.aidesktopagent.actions.ActionPlan;
import com.aidesktopagent.actions.ActionType;
import com.aidesktopagent.actions.ActionValidator;
import com.aidesktopagent.ai.RuleBasedAIClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end planning tests (no real OS side effects).
 */
class AgentPlanTest {

    private Agent agent;

    @BeforeEach
    void setUp() {
        agent = new Agent(
                new RuleBasedAIClient(),
                new ActionParser(),
                new ActionValidator(),
                null, // no executor — plan only
                a -> false,
                null
        );
    }

    @Test
    void openChromeProducesValidatedPlan() throws Exception {
        ActionPlan plan = agent.plan("Open Chrome");
        assertFalse(plan.isEmpty());
        assertEquals(ActionType.OPEN_APPLICATION, plan.getActions().get(0).getType());
        String target = plan.getActions().get(0).getTarget().orElse("");
        assertTrue(target.toLowerCase().contains("chrome"), "target was: " + target);
        System.out.println("Validated plan: " + plan.getActions().get(0));
    }

    @Test
    void createFolderPlan() throws Exception {
        ActionPlan plan = agent.plan("Create a folder called AI Projects");
        assertEquals(ActionType.CREATE_FOLDER, plan.getActions().get(0).getType());
        assertEquals("AI Projects", plan.getActions().get(0).getTarget().orElseThrow());
    }
}

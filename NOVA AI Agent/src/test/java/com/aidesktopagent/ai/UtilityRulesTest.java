package com.aidesktopagent.ai;

import com.aidesktopagent.actions.ActionParser;
import com.aidesktopagent.actions.ActionType;
import com.aidesktopagent.actions.ActionValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UtilityRulesTest {

    private ActionType type(String cmd) throws Exception {
        String json = new RuleBasedAIClient().planActions(cmd);
        var plan = new ActionParser().parse(cmd, json);
        var vr = new ActionValidator().validate(plan);
        assertTrue(vr.hasAcceptedActions(), vr.getRejections().toString());
        return vr.getPlan().getActions().get(0).getType();
    }

    @Test
    void utilityIntents() throws Exception {
        assertEquals(ActionType.GET_TIME, type("what time is it"));
        assertEquals(ActionType.CALCULATE, type("calculate 2 + 2"));
        assertEquals(ActionType.SHOW_HELP, type("help"));
        assertEquals(ActionType.LIST_NOTES, type("list notes"));
        assertEquals(ActionType.GIT_STATUS, type("git status"));
        assertEquals(ActionType.SYSTEM_INFO, type("system info"));
    }
}

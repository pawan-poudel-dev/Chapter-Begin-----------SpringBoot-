package com.aidesktopagent.agent;

import com.aidesktopagent.actions.Action;
import com.aidesktopagent.actions.ActionPlan;
import com.aidesktopagent.actions.ActionType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlanValidatorTest {

    @Test
    void rejectsEmpty() {
        var r = new PlanValidator().validate(new ActionPlan("x", List.of(), null));
        assertFalse(r.acceptable());
    }

    @Test
    void acceptsSmallPlan() {
        var r = new PlanValidator().validate(new ActionPlan("x",
                List.of(Action.builder(ActionType.SEARCH_WEB).target("java").build()), null));
        assertTrue(r.acceptable());
    }

    @Test
    void rejectsOversized() {
        List<Action> many = new ArrayList<>();
        for (int i = 0; i < PlanValidator.MAX_ACTIONS + 2; i++) {
            many.add(Action.builder(ActionType.SEARCH_WEB).target("q" + i).build());
        }
        var r = new PlanValidator().validate(new ActionPlan("x", many, null));
        assertFalse(r.acceptable());
    }
}

package com.aidesktopagent.security;

import com.aidesktopagent.actions.Action;
import com.aidesktopagent.actions.ActionResult;
import com.aidesktopagent.actions.ActionType;
import com.aidesktopagent.computer.ActionExecutor;
import com.aidesktopagent.computer.PermissionGuardedExecutor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PermissionEnforcementTest {

    private static final ActionExecutor INNER = new ActionExecutor() {
        @Override
        public boolean supports(Action action) {
            return true;
        }

        @Override
        public ActionResult execute(Action action) {
            return ActionResult.success(action.getType(), "should not run");
        }
    };

    @Test
    void shellDeniedByDefault() throws Exception {
        PermissionRegistry perms = new PermissionRegistry();
        assertFalse(perms.allows(ActionType.RUN_COMMAND));
        PermissionGuardedExecutor guarded = new PermissionGuardedExecutor(INNER, perms);
        ActionResult r = guarded.execute(Action.builder(ActionType.RUN_COMMAND).target("ls").build());
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().toLowerCase().contains("permission"));
    }

    @Test
    void revokeWriteBlocksDelete() throws Exception {
        PermissionRegistry perms = new PermissionRegistry();
        perms.revoke(PermissionRegistry.Scope.FILES_WRITE);
        assertFalse(perms.allows(ActionType.DELETE_FILE));
        var r = new PermissionGuardedExecutor(INNER, perms)
                .execute(Action.builder(ActionType.DELETE_FILE).target("x").build());
        assertFalse(r.isSuccess());
    }
}

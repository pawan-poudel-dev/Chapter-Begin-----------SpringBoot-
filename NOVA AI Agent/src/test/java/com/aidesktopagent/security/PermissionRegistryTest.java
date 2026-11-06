package com.aidesktopagent.security;

import com.aidesktopagent.actions.ActionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PermissionRegistryTest {

    @Test
    void lockedDefaults() {
        PermissionRegistry r = new PermissionRegistry();
        assertTrue(r.allows(ActionType.OPEN_WEBSITE));
        assertTrue(r.allows(ActionType.SEARCH_WEB));
        assertFalse(r.allows(ActionType.DELETE_FILE));
        assertFalse(r.allows(ActionType.ANALYZE_PROJECT));
        assertFalse(r.allows(ActionType.RUN_COMMAND));
    }

    @Test
    void interactiveDesktopGrantsWriteAndCoding() {
        PermissionRegistry r = PermissionRegistry.interactiveDesktop();
        assertTrue(r.allows(ActionType.CREATE_FILE));
        assertTrue(r.allows(ActionType.ANALYZE_PROJECT));
        assertTrue(r.allows(ActionType.RUN_BUILD));
        assertFalse(r.allows(ActionType.RUN_COMMAND));
    }

    @Test
    void revokeBlocks() {
        PermissionRegistry r = new PermissionRegistry();
        r.revoke(PermissionRegistry.Scope.SHELL);
        assertFalse(r.allows(ActionType.RUN_COMMAND));
    }

    @Test
    void riskLevels() {
        PermissionRegistry r = new PermissionRegistry();
        assertEquals(PermissionRegistry.RiskLevel.CRITICAL, r.riskOf(ActionType.DELETE_FILE));
        assertEquals(PermissionRegistry.RiskLevel.LOW, r.riskOf(ActionType.SEARCH_WEB));
    }
}

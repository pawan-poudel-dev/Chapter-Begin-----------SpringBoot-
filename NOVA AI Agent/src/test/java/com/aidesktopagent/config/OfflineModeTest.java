package com.aidesktopagent.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OfflineModeTest {

    @Test
    void forcedOffline() {
        OfflineMode m = new OfflineMode(true, true, true);
        assertTrue(m.isOffline());
        assertFalse(m.canUseCloudAi());
        assertTrue(m.statusMessage().toLowerCase().contains("offline"));
    }

    @Test
    void onlineWithKey() {
        OfflineMode m = new OfflineMode(false, true, true);
        assertFalse(m.isOffline());
        assertTrue(m.canUseCloudAi());
    }
}

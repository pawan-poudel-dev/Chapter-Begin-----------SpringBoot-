package com.aidesktopagent;

import com.aidesktopagent.config.AppConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Basic smoke tests for foundation classes.
 */
class MainTest {

    @Test
    void appConfigLoadsWithDefaults() {
        AppConfig config = AppConfig.load();
        assertNotNull(config);
        assertEquals("AI Desktop Agent", config.getAppName());
        assertEquals("0.1.0-SNAPSHOT", config.getAppVersion());
        assertNotNull(config.getLogLevel());
    }
}

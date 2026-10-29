package com.aidesktopagent.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FastIntentRouterTest {

    @Test
    void exactMatches() {
        FastIntentRouter r = new FastIntentRouter();
        assertEquals("PAUSE_MEDIA", r.match("pause").orElseThrow().actionType());
        assertEquals("ANALYZE_PROJECT", r.match("analyze project").orElseThrow().actionType());
        assertEquals("OPEN_APPLICATION", r.match("open chrome").orElseThrow().actionType());
    }

    @Test
    void toJson() {
        FastIntentRouter r = new FastIntentRouter();
        var i = r.match("run tests").orElseThrow();
        String json = r.toJson(i);
        assertTrue(json.contains("RUN_BUILD"));
        assertTrue(json.contains("test"));
    }
}

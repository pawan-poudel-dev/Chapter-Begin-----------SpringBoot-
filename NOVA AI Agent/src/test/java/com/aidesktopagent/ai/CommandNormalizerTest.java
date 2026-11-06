package com.aidesktopagent.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommandNormalizerTest {

    @Test
    void synonyms() {
        CommandNormalizer n = new CommandNormalizer();
        assertTrue(n.normalize("launch Chrome").toLowerCase().startsWith("open"));
        assertEquals("pause", n.normalize("stop the music"));
    }

    @Test
    void lowConfidence() {
        CommandNormalizer n = new CommandNormalizer();
        assertTrue(n.isLowConfidence("um"));
        assertTrue(n.isLowConfidence(""));
        assertFalse(n.isLowConfidence("Open Chrome"));
    }

    @Test
    void destructiveAmbiguous() {
        CommandNormalizer n = new CommandNormalizer();
        assertTrue(n.isDestructiveAmbiguous("delete that"));
        assertFalse(n.isDestructiveAmbiguous("delete the file notes.txt"));
    }
}

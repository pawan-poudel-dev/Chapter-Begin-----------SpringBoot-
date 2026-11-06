package com.aidesktopagent.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeminiClientHelpersTest {

    @Test
    void stripsJsonFences() {
        String raw = "```json\n{\"actions\":[]}\n```";
        assertEquals("{\"actions\":[]}", OpenAiCompatibleClient.stripMarkdownFences(raw));
    }

    @Test
    void plainJsonUnchanged() {
        assertEquals("{\"a\":1}", OpenAiCompatibleClient.stripMarkdownFences("{\"a\":1}"));
    }
}

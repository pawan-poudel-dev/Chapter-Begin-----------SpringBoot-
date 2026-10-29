package com.aidesktopagent.voice;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MockSpeechToTextTest {

    @Test
    void returnsFixedText() throws Exception {
        MockSpeechToText stt = new MockSpeechToText("Open Chrome");
        assertEquals("Open Chrome", stt.transcribe(new byte[]{1, 2, 3}, Locale.ENGLISH));
        assertEquals("mock-stt", stt.providerName());
    }

    @Test
    void unclearThrows() {
        MockSpeechToText stt = new MockSpeechToText(() -> "x", true);
        SpeechRecognitionException ex = assertThrows(SpeechRecognitionException.class,
                () -> stt.transcribe(new byte[10], null));
        assertEquals(SpeechRecognitionException.Kind.UNCLEAR, ex.getKind());
    }

    @Test
    void emptySupplierThrows() {
        MockSpeechToText stt = new MockSpeechToText(() -> "  ", false);
        assertThrows(SpeechRecognitionException.class,
                () -> stt.transcribe(new byte[10], Locale.ENGLISH));
    }

    @Test
    void scriptAdvances() throws Exception {
        AtomicInteger i = new AtomicInteger();
        String[] phrases = {"Open Chrome", "Open YouTube"};
        MockSpeechToText stt = new MockSpeechToText(
                () -> phrases[Math.min(i.getAndIncrement(), phrases.length - 1)], false);
        assertEquals("Open Chrome", stt.transcribe(new byte[1], null));
        assertEquals("Open YouTube", stt.transcribe(new byte[1], null));
    }
}

package com.aidesktopagent.voice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConsoleTextToSpeechTest {

    @Test
    void speakDoesNotThrow() {
        ConsoleTextToSpeech tts = new ConsoleTextToSpeech();
        assertDoesNotThrow(() -> tts.speak("I'm listening."));
        assertDoesNotThrow(() -> tts.speak(""));
        assertDoesNotThrow(() -> tts.speak(null));
        assertEquals("console", tts.providerName());
    }

    @Test
    void compositeFallsBack() throws Exception {
        TextToSpeech failing = new TextToSpeech() {
            @Override
            public void speak(String text) throws SpeechSynthesisException {
                throw new SpeechSynthesisException("no hardware");
            }

            @Override
            public String providerName() {
                return "fail";
            }
        };
        CompositeTextToSpeech composite = new CompositeTextToSpeech(
                java.util.List.of(failing, new ConsoleTextToSpeech()));
        assertDoesNotThrow(() -> composite.speak("Opening Chrome."));
    }
}

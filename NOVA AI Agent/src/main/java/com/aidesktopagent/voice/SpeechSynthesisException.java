package com.aidesktopagent.voice;

public class SpeechSynthesisException extends Exception {

    public SpeechSynthesisException(String message) {
        super(message);
    }

    public SpeechSynthesisException(String message, Throwable cause) {
        super(message, cause);
    }
}

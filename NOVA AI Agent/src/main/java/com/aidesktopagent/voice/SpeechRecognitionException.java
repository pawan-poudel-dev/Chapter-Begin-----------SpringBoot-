package com.aidesktopagent.voice;

public class SpeechRecognitionException extends Exception {

    public enum Kind {
        MICROPHONE_UNAVAILABLE,
        PERMISSION_DENIED,
        NETWORK,
        TIMEOUT,
        UNCLEAR,
        PROVIDER,
        CANCELLED
    }

    private final Kind kind;

    public SpeechRecognitionException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public SpeechRecognitionException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}

package com.aidesktopagent.ai;

public class AIClientException extends Exception {

    public AIClientException(String message) {
        super(message);
    }

    public AIClientException(String message, Throwable cause) {
        super(message, cause);
    }
}

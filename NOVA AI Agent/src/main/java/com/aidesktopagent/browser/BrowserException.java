package com.aidesktopagent.browser;

public class BrowserException extends Exception {

    public BrowserException(String message) {
        super(message);
    }

    public BrowserException(String message, Throwable cause) {
        super(message, cause);
    }
}

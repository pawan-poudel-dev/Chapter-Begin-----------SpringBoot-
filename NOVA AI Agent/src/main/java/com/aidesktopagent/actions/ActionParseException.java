package com.aidesktopagent.actions;

/**
 * Thrown when AI output cannot be parsed into a valid ActionPlan.
 */
public class ActionParseException extends Exception {

    public ActionParseException(String message) {
        super(message);
    }

    public ActionParseException(String message, Throwable cause) {
        super(message, cause);
    }
}

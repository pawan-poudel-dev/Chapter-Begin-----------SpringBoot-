package com.aidesktopagent.actions;

import java.util.Objects;

/**
 * Outcome of executing a single action.
 */
public final class ActionResult {

    private final boolean success;
    private final ActionType actionType;
    private final String message;
    private final String detail;

    private ActionResult(boolean success, ActionType actionType, String message, String detail) {
        this.success = success;
        this.actionType = Objects.requireNonNull(actionType, "actionType");
        this.message = message != null ? message : "";
        this.detail = detail;
    }

    public static ActionResult success(ActionType type, String message) {
        return new ActionResult(true, type, message, null);
    }

    public static ActionResult success(ActionType type, String message, String detail) {
        return new ActionResult(true, type, message, detail);
    }

    public static ActionResult failure(ActionType type, String message) {
        return new ActionResult(false, type, message, null);
    }

    public static ActionResult failure(ActionType type, String message, String detail) {
        return new ActionResult(false, type, message, detail);
    }

    public static ActionResult cancelled(ActionType type, String message) {
        return new ActionResult(false, type, message != null ? message : "Cancelled by user", "cancelled");
    }

    public boolean isSuccess() {
        return success;
    }

    public ActionType getActionType() {
        return actionType;
    }

    public String getMessage() {
        return message;
    }

    public String getDetail() {
        return detail;
    }

    @Override
    public String toString() {
        return "ActionResult{success=" + success +
                ", action=" + actionType +
                ", message='" + message + '\'' +
                (detail != null ? ", detail='" + detail + '\'' : "") +
                '}';
    }
}

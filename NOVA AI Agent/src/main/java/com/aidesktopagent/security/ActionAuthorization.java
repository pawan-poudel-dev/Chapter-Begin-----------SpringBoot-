package com.aidesktopagent.security;

import com.aidesktopagent.actions.Action;
import com.aidesktopagent.actions.ActionType;

import java.util.Objects;

/**
 * Central authorization gate between validation and execution.
 * Prompt-level safety is not a substitute for this check.
 */
public final class ActionAuthorization {

    private final PermissionRegistry permissions;

    public ActionAuthorization(PermissionRegistry permissions) {
        this.permissions = Objects.requireNonNull(permissions);
    }

    public void requireAllowed(Action action) {
        if (action == null) {
            throw new SecurityException("Missing action");
        }
        if (!permissions.allows(action.getType())) {
            throw new SecurityException("Permission denied for action: " + action.getType()
                    + " (scope not granted)");
        }
    }

    public boolean isAllowed(ActionType type) {
        return permissions.allows(type);
    }

    public PermissionRegistry registry() {
        return permissions;
    }
}

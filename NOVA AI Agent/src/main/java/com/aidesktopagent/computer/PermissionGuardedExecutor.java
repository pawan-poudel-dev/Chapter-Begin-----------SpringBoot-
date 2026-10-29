package com.aidesktopagent.computer;

import com.aidesktopagent.actions.Action;
import com.aidesktopagent.actions.ActionResult;
import com.aidesktopagent.logging.LoggingService;
import com.aidesktopagent.security.PermissionRegistry;

import java.util.Objects;

/** Wraps any executor with permission checks (Day 35). */
public final class PermissionGuardedExecutor implements ActionExecutor {

    private final ActionExecutor delegate;
    private final PermissionRegistry permissions;
    private final LoggingService log;

    public PermissionGuardedExecutor(ActionExecutor delegate, PermissionRegistry permissions) {
        this(delegate, permissions, LoggingService.forComponent("Permissions"));
    }

    public PermissionGuardedExecutor(ActionExecutor delegate, PermissionRegistry permissions, LoggingService log) {
        this.delegate = Objects.requireNonNull(delegate);
        this.permissions = Objects.requireNonNull(permissions);
        this.log = log != null ? log : LoggingService.forComponent("Permissions");
    }

    @Override
    public boolean supports(Action action) {
        return delegate.supports(action);
    }

    @Override
    public ActionResult execute(Action action) throws Exception {
        if (!permissions.allows(action.getType())) {
            log.warn("Permission denied for " + action.getType());
            return ActionResult.failure(action.getType(),
                    "Permission denied for " + action.getType()
                            + " (scope not granted). Enable it in permission settings.");
        }
        return delegate.execute(action);
    }
}

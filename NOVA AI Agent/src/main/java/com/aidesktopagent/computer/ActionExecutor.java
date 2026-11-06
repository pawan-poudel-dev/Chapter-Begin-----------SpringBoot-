package com.aidesktopagent.computer;

import com.aidesktopagent.actions.Action;
import com.aidesktopagent.actions.ActionResult;

/**
 * Executes a single validated {@link Action}.
 * Implementations must assume the action has already passed {@code ActionValidator}.
 */
public interface ActionExecutor {

    /**
     * @return whether this executor can handle the given action
     */
    boolean supports(Action action);

    /**
     * Execute the action and return a structured result.
     * Must not throw for expected failures; use {@link ActionResult#failure} instead.
     * May throw for catastrophic/unexpected errors.
     */
    ActionResult execute(Action action) throws Exception;
}

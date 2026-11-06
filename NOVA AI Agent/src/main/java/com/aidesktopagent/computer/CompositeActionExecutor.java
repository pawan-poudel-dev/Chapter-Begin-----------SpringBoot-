package com.aidesktopagent.computer;

import com.aidesktopagent.actions.Action;
import com.aidesktopagent.actions.ActionResult;
import com.aidesktopagent.actions.ActionType;
import com.aidesktopagent.logging.LoggingService;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** O(1) ActionType routing built at startup for instant dispatch. */
public final class CompositeActionExecutor implements ActionExecutor {

    private final List<ActionExecutor> executors;
    private final Map<ActionType, ActionExecutor> route;
    private final LoggingService log;

    public CompositeActionExecutor(List<ActionExecutor> executors) {
        this(executors, LoggingService.forComponent("CompositeExecutor"));
    }

    public CompositeActionExecutor(List<ActionExecutor> executors, LoggingService log) {
        this.executors = new ArrayList<>(Objects.requireNonNull(executors));
        this.log = log != null ? log : LoggingService.forComponent("CompositeExecutor");
        this.route = buildRoute(this.executors);
    }

    private static Map<ActionType, ActionExecutor> buildRoute(List<ActionExecutor> executors) {
        Map<ActionType, ActionExecutor> map = new EnumMap<>(ActionType.class);
        for (ActionType type : ActionType.values()) {
            Action probe = Action.builder(type).target("_").build();
            for (ActionExecutor e : executors) {
                try {
                    if (e.supports(probe)) {
                        map.putIfAbsent(type, e);
                        break;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return map;
    }

    @Override
    public boolean supports(Action action) {
        return route.containsKey(action.getType());
    }

    @Override
    public ActionResult execute(Action action) throws Exception {
        ActionExecutor e = route.get(action.getType());
        if (e != null) {
            return e.execute(action);
        }
        for (ActionExecutor ex : executors) {
            if (ex.supports(action)) {
                return ex.execute(action);
            }
        }
        return ActionResult.failure(action.getType(),
                "No executor registered for " + action.getType());
    }

    public int routedTypeCount() {
        return route.size();
    }
}

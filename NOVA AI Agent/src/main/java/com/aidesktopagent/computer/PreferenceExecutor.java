package com.aidesktopagent.computer;

import com.aidesktopagent.actions.Action;
import com.aidesktopagent.actions.ActionResult;
import com.aidesktopagent.actions.ActionType;
import com.aidesktopagent.memory.PreferencesStore;

import java.util.Objects;

public final class PreferenceExecutor implements ActionExecutor {

    private final PreferencesStore store;

    public PreferenceExecutor(PreferencesStore store) {
        this.store = Objects.requireNonNull(store);
    }

    @Override
    public boolean supports(Action action) {
        return action.getType() == ActionType.SET_PREFERENCE
                || action.getType() == ActionType.GET_PREFERENCE;
    }

    @Override
    public ActionResult execute(Action action) {
        try {
            if (action.getType() == ActionType.GET_PREFERENCE) {
                String key = action.getTarget().orElse("");
                if (key.isBlank()) {
                    return ActionResult.success(ActionType.GET_PREFERENCE,
                            "Preferences: " + store.all());
                }
                return store.get(key)
                        .map(v -> ActionResult.success(ActionType.GET_PREFERENCE, key + "=" + v))
                        .orElse(ActionResult.failure(ActionType.GET_PREFERENCE, "No preference: " + key));
            }
            String key = action.getTarget().orElseThrow();
            String value = action.getParameter("value").orElse("");
            store.set(key, value);
            return ActionResult.success(ActionType.SET_PREFERENCE, "Saved " + key + "=" + value);
        } catch (Exception e) {
            return ActionResult.failure(action.getType(), e.getMessage());
        }
    }
}

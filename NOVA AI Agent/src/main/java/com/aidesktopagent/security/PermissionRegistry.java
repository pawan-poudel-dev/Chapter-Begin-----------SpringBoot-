package com.aidesktopagent.security;

import com.aidesktopagent.actions.ActionType;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central permission scopes enforced at execution time (Day 35).
 */
public final class PermissionRegistry {

    public enum Scope {
        APPLICATIONS,
        FILES_READ,
        FILES_WRITE,
        BROWSER,
        MEDIA,
        CODING,
        BUILD,
        PREFERENCES,
        SHELL
    }

    public enum RiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    private final Set<Scope> granted = ConcurrentHashMap.newKeySet();
    private final Map<ActionType, Scope> actionScopes = new EnumMap<>(ActionType.class);

    public PermissionRegistry() {
        // Locked-down defaults: read/browse/media only. Writes, coding, build, shell off.
        granted.add(Scope.APPLICATIONS);
        granted.add(Scope.FILES_READ);
        granted.add(Scope.BROWSER);
        granted.add(Scope.MEDIA);
        granted.add(Scope.PREFERENCES);
        mapDefaults();
    }

    /** Interactive desktop profile: enables write/coding/build with explicit grants (not shell). */
    public static PermissionRegistry interactiveDesktop() {
        PermissionRegistry r = new PermissionRegistry();
        r.grant(Scope.FILES_WRITE);
        r.grant(Scope.CODING);
        r.grant(Scope.BUILD);
        return r;
    }

    /** Fully locked: only scopes added via grant(). */
    public static PermissionRegistry lockedDown() {
        PermissionRegistry r = new PermissionRegistry();
        r.revoke(Scope.APPLICATIONS);
        r.revoke(Scope.FILES_READ);
        r.revoke(Scope.BROWSER);
        r.revoke(Scope.MEDIA);
        r.revoke(Scope.PREFERENCES);
        return r;
    }

    private void mapDefaults() {
        for (ActionType t : ActionType.values()) {
            if (t.isMediaRelated()) {
                actionScopes.put(t, Scope.MEDIA);
            } else if (t.isCodingRelated()) {
                actionScopes.put(t, t == ActionType.RUN_BUILD ? Scope.BUILD : Scope.CODING);
            } else {
                actionScopes.put(t, switch (t) {
                    case OPEN_APPLICATION, CLOSE_APPLICATION -> Scope.APPLICATIONS;
                    case OPEN_WEBSITE, SEARCH_WEB -> Scope.BROWSER;
                    case READ_FILE, SEARCH_FILES, SUMMARIZE_FOLDER, OPEN_FOLDER -> Scope.FILES_READ;
                    case CREATE_FILE, CREATE_FOLDER, MOVE_FILE, COPY_FILE, DELETE_FILE -> Scope.FILES_WRITE;
                    case SET_PREFERENCE, GET_PREFERENCE -> Scope.PREFERENCES;
                    case RUN_COMMAND -> Scope.SHELL;
                    default -> Scope.APPLICATIONS;
                });
            }
        }
    }

    public void grant(Scope scope) {
        granted.add(scope);
    }

    public void revoke(Scope scope) {
        granted.remove(scope);
    }

    public boolean isGranted(Scope scope) {
        return granted.contains(scope);
    }

    public boolean allows(ActionType type) {
        Scope s = actionScopes.getOrDefault(type, Scope.APPLICATIONS);
        return granted.contains(s);
    }

    public RiskLevel riskOf(ActionType type) {
        if (type.isDestructive()) {
            return RiskLevel.CRITICAL;
        }
        return switch (type) {
            case RUN_BUILD, APPLY_CODE_CHANGE, OPEN_IN_IDE -> RiskLevel.HIGH;
            case TYPE_TEXT, PRESS_KEY, CREATE_FILE, CREATE_FOLDER, MOVE_FILE, COPY_FILE -> RiskLevel.MEDIUM;
            default -> RiskLevel.LOW;
        };
    }

    public Set<Scope> grantedScopes() {
        return EnumSet.copyOf(granted);
    }
}

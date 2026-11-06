package com.aidesktopagent.plugins;

/**
 * Optional integration contract (Day 34). No arbitrary code loading.
 */
public interface Integration {

    String id();

    String displayName();

    boolean isAvailable();

    /** Human-readable status for settings UI. */
    String statusMessage();

    void shutdown();
}

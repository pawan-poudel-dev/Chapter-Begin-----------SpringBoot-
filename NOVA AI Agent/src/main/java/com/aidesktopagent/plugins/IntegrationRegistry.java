package com.aidesktopagent.plugins;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Registers optional integrations; isolates failures (Day 34). */
public final class IntegrationRegistry {

    private final Map<String, Integration> integrations = new ConcurrentHashMap<>();

    public void register(Integration integration) {
        if (integration != null && integration.id() != null) {
            integrations.put(integration.id(), integration);
        }
    }

    public Optional<Integration> get(String id) {
        return Optional.ofNullable(integrations.get(id));
    }

    public Collection<Integration> all() {
        return integrations.values();
    }

    public void shutdownAll() {
        for (Integration i : integrations.values()) {
            try {
                i.shutdown();
            } catch (Exception ignored) {
            }
        }
    }
}

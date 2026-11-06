package com.aidesktopagent.computer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configurable allowlist of applications (Day 11).
 * Only registered names/aliases can be launched via OPEN_APPLICATION.
 */
public final class ApplicationRegistry {

    public record AppEntry(String name, Path executable, String alias) {
    }

    private final Map<String, AppEntry> byKey = new ConcurrentHashMap<>();

    public ApplicationRegistry() {
        registerDefaults();
    }

    private void registerDefaults() {
        // Logical aliases — resolvers try common binaries
        registerAlias("browser", "google-chrome", "chromium", "firefox", "chrome");
        registerAlias("ide", "code", "idea");
        registerAlias("terminal", "x-terminal-emulator", "gnome-terminal", "konsole", "cmd");
        registerAlias("files", "nautilus", "explorer", "open");
        registerSimple("notepad", "notepad");
        registerSimple("calculator", "gnome-calculator", "calc");
    }

    public void register(String name, Path executable) {
        if (name == null || name.isBlank() || executable == null) {
            return;
        }
        String key = name.toLowerCase(Locale.ROOT).trim();
        byKey.put(key, new AppEntry(name, executable, key));
    }

    public void registerSimple(String name, String... candidates) {
        for (String c : candidates) {
            Optional<Path> p = which(c);
            if (p.isPresent()) {
                register(name, p.get());
                return;
            }
        }
        // Keep name resolvable by basename even if not found yet
        byKey.putIfAbsent(name.toLowerCase(Locale.ROOT), new AppEntry(name, Path.of(name), name.toLowerCase(Locale.ROOT)));
    }

    public void registerAlias(String alias, String... candidates) {
        for (String c : candidates) {
            Optional<Path> p = which(c);
            if (p.isPresent()) {
                byKey.put(alias.toLowerCase(Locale.ROOT), new AppEntry(alias, p.get(), alias));
                return;
            }
        }
        byKey.putIfAbsent(alias.toLowerCase(Locale.ROOT),
                new AppEntry(alias, Path.of(candidates[0]), alias.toLowerCase(Locale.ROOT)));
    }

    public Optional<AppEntry> resolve(String nameOrAlias) {
        if (nameOrAlias == null || nameOrAlias.isBlank()) {
            return Optional.empty();
        }
        String key = nameOrAlias.toLowerCase(Locale.ROOT).trim();
        AppEntry e = byKey.get(key);
        if (e != null) {
            return Optional.of(e);
        }
        // fuzzy: contains
        return byKey.values().stream()
                .filter(a -> a.name().toLowerCase(Locale.ROOT).contains(key)
                        || a.alias().contains(key))
                .findFirst();
    }

    public boolean isApproved(String nameOrAlias) {
        return resolve(nameOrAlias).isPresent();
    }

    public Collection<AppEntry> all() {
        return byKey.values();
    }

    public Optional<String> missingMessage(String name) {
        Optional<AppEntry> e = resolve(name);
        if (e.isEmpty()) {
            return Optional.of("Application not in registry: " + name
                    + ". Add it via ApplicationRegistry or use an alias (browser, ide, terminal).");
        }
        Path exe = e.get().executable();
        if (!Files.isExecutable(exe) && !Files.isRegularFile(exe)) {
            return Optional.of("Registered application not found on disk: " + exe
                    + " (" + e.get().name() + ")");
        }
        return Optional.empty();
    }

    private static Optional<Path> which(String cmd) {
        try {
            Process p = new ProcessBuilder("which", cmd).start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            if (p.waitFor() == 0 && !out.isBlank()) {
                return Optional.of(Path.of(out));
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }
}

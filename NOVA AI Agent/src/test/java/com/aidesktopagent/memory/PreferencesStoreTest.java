package com.aidesktopagent.memory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PreferencesStoreTest {

    @TempDir
    Path temp;

    @Test
    void setGetPersist() {
        Path f = temp.resolve("prefs.json");
        PreferencesStore s = new PreferencesStore(f);
        s.set("browser", "firefox");
        assertEquals("firefox", s.get("browser").orElseThrow());

        PreferencesStore s2 = new PreferencesStore(f);
        assertEquals("firefox", s2.get("browser").orElseThrow());
    }

    @Test
    void rejectsSecrets() {
        PreferencesStore s = new PreferencesStore(temp.resolve("p.json"));
        assertThrows(IllegalArgumentException.class, () -> s.set("browser", "sk-secret-key"));
        assertThrows(IllegalArgumentException.class, () -> s.set("password", "x"));
    }

    @Test
    void clear() {
        PreferencesStore s = new PreferencesStore(temp.resolve("p2.json"));
        s.set("theme", "dark");
        s.clear();
        assertTrue(s.all().isEmpty());
    }
}

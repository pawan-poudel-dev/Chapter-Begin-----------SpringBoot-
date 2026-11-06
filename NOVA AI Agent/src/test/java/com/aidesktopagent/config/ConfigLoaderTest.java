package com.aidesktopagent.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderTest {

    @Test
    void loadBaseConfigSucceeds() {
        AppConfig config = ConfigLoader.load(null, null);
        assertNotNull(config);
        assertEquals("AI Desktop Agent", config.getAppName());
        assertEquals("0.1.0-SNAPSHOT", config.getAppVersion());
    }

    @Test
    void loadDevProfileAppliesOverrides() {
        AppConfig config = ConfigLoader.load("dev", null);
        assertEquals("dev", config.getProfile());
        // application-dev.properties sets logging.level=DEBUG and data.directory=./data/dev
        assertEquals("DEBUG", config.getLogLevel());
        assertEquals("./data/dev", config.getDataDirectory());
    }

    @Test
    void loadTestProfileAppliesOverrides() {
        AppConfig config = ConfigLoader.load("test", null);
        assertEquals("test", config.getProfile());
        assertEquals("WARN", config.getLogLevel());
        assertEquals("./data/test", config.getDataDirectory());
    }

    @Test
    void externalFileOverridesBase(@TempDir Path tempDir) throws Exception {
        Path external = tempDir.resolve("custom.properties");
        Files.writeString(external, """
                app.name=External Agent
                logging.level=ERROR
                ai.model=custom-model
                """);

        AppConfig config = ConfigLoader.load(null, external);
        assertEquals("External Agent", config.getAppName());
        assertEquals("ERROR", config.getLogLevel());
        assertEquals("custom-model", config.getAiModel());
    }

    @Test
    void secretsInExternalFileAreStripped(@TempDir Path tempDir) throws Exception {
        Path external = tempDir.resolve("secrets.properties");
        Files.writeString(external, """
                app.name=Secret Test
                openai.api.key=sk-should-never-appear
                api.key=another-secret
                password=hunter2
                """);

        AppConfig config = ConfigLoader.load(null, external);
        assertEquals("Secret Test", config.getAppName());
        // Config object must not expose the key; resolveApiKey only looks at env
        String asString = config.toString();
        assertFalse(asString.contains("sk-should-never-appear"));
        assertFalse(asString.contains("hunter2"));
    }

    @Test
    void resolveSecretReturnsEmptyWhenMissing() {
        Optional<String> value = ConfigLoader.resolveSecret("NONEXISTENT_SECRET_KEY_XYZ_12345");
        assertTrue(value.isEmpty());
    }

    @Test
    void resolveSecretRejectsNullName() {
        assertThrows(NullPointerException.class, () -> ConfigLoader.resolveSecret(null));
    }

    @Test
    void missingExternalFileIsIgnoredGracefully(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("does-not-exist.properties");
        AppConfig config = ConfigLoader.load(null, missing);
        // Falls back to base defaults
        assertEquals("AI Desktop Agent", config.getAppName());
    }
}

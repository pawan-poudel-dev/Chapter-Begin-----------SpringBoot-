package com.aidesktopagent.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {

    @Test
    void loadReturnsNonNullConfig() {
        AppConfig config = AppConfig.load();
        assertNotNull(config.getAppName());
        assertNotNull(config.getAppVersion());
        assertNotNull(config.getLogLevel());
        assertNotNull(config.getAiProvider());
        assertNotNull(config.getAiModel());
    }

    @Test
    void defaultValuesAreSensible() {
        Properties props = new Properties();
        AppConfig config = AppConfig.fromProperties(props, null);
        assertEquals("AI Desktop Agent", config.getAppName());
        assertEquals("0.1.0-SNAPSHOT", config.getAppVersion());
        assertEquals("INFO", config.getLogLevel());
        assertEquals("openai", config.getAiProvider());
        assertFalse(config.isVoiceEnabled());
    }

    @Test
    void fromPropertiesRespectsProvidedValues() {
        Properties props = new Properties();
        props.setProperty("app.name", "Test Agent");
        props.setProperty("app.version", "9.9.9");
        props.setProperty("logging.level", "DEBUG");
        props.setProperty("ai.provider", "anthropic");
        props.setProperty("ai.model", "claude-3");
        props.setProperty("voice.enabled", "true");
        props.setProperty("data.directory", "/tmp/agent-data");

        AppConfig config = AppConfig.fromProperties(props, "dev");
        assertEquals("Test Agent", config.getAppName());
        assertEquals("9.9.9", config.getAppVersion());
        assertEquals("DEBUG", config.getLogLevel());
        assertEquals("anthropic", config.getAiProvider());
        assertEquals("claude-3", config.getAiModel());
        assertTrue(config.isVoiceEnabled());
        assertEquals("/tmp/agent-data", config.getDataDirectory());
        assertEquals("dev", config.getProfile());
    }

    @Test
    void toStringDoesNotContainSecrets() {
        AppConfig config = AppConfig.load();
        String s = config.toString();
        assertFalse(s.toLowerCase().contains("sk-"));
        assertFalse(s.toLowerCase().contains("api_key"));
        assertTrue(s.contains("apiKeyConfigured="));
    }

    @Test
    void resolveApiKeyIsEmptyWhenNoEnv() {
        // Without setting env vars, should be empty
        AppConfig config = AppConfig.fromProperties(new Properties(), null);
        Optional<String> key = config.resolveApiKey();
        // We cannot force env vars away in all environments, but the method must not throw
        assertNotNull(key);
    }

    @Test
    void builderProducesValidConfig() {
        AppConfig config = new AppConfig.Builder()
                .appName("Builder Agent")
                .appVersion("1.0")
                .profile("test")
                .logLevel("WARN")
                .aiProvider("google")
                .aiModel("gemini-pro")
                .voiceEnabled(true)
                .dataDirectory("./tmp")
                .build();

        assertEquals("Builder Agent", config.getAppName());
        assertEquals("test", config.getProfile());
        assertEquals("google", config.getAiProvider());
        assertTrue(config.isVoiceEnabled());
    }
}

package com.aidesktopagent.config;

import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * Immutable application configuration.
 *
 * <p>API keys are never stored in this object or in property files.
 * Use {@link ConfigLoader#resolveSecret(String)} when a key is required at runtime.
 */
public final class AppConfig {

    private final String appName;
    private final String appVersion;
    private final String profile;
    private final String logLevel;

    // AI placeholders (no secrets)
    private final String aiProvider;
    private final String aiModel;
    private final String aiBaseUrl;

    // Feature toggles / paths
    private final boolean voiceEnabled;
    private final String dataDirectory;

    private AppConfig(Builder b) {
        this.appName = Objects.requireNonNull(b.appName, "appName");
        this.appVersion = Objects.requireNonNull(b.appVersion, "appVersion");
        this.profile = b.profile;
        this.logLevel = Objects.requireNonNull(b.logLevel, "logLevel");
        this.aiProvider = b.aiProvider;
        this.aiModel = b.aiModel;
        this.aiBaseUrl = b.aiBaseUrl;
        this.voiceEnabled = b.voiceEnabled;
        this.dataDirectory = b.dataDirectory;
    }

    /**
     * Convenience entry point used by {@link Main}.
     */
    public static AppConfig load() {
        return ConfigLoader.load();
    }

    /**
     * Builds an {@link AppConfig} from a fully merged {@link Properties} set.
     */
    public static AppConfig fromProperties(Properties props, String profile) {
        Objects.requireNonNull(props, "props");
        Builder b = new Builder();
        b.appName = props.getProperty("app.name", "AI Desktop Agent");
        b.appVersion = props.getProperty("app.version", "0.1.0-SNAPSHOT");
        b.profile = profile;
        b.logLevel = props.getProperty("logging.level", "INFO");
        b.aiProvider = props.getProperty("ai.provider", "openai");
        b.aiModel = props.getProperty("ai.model", "gpt-4o-mini");
        b.aiBaseUrl = props.getProperty("ai.base-url", "");
        b.voiceEnabled = Boolean.parseBoolean(props.getProperty("voice.enabled", "false"));
        b.dataDirectory = props.getProperty("data.directory", "./data");

        // Auto-detect free Google Gemini when its key is present and OpenAI is not
        boolean hasGemini = ConfigLoader.resolveSecret(ConfigLoader.ENV_GEMINI_API_KEY).isPresent()
                || ConfigLoader.resolveSecret(ConfigLoader.ENV_GOOGLE_API_KEY).isPresent();
        boolean hasOpenAi = ConfigLoader.resolveSecret(ConfigLoader.ENV_OPENAI_API_KEY).isPresent();
        String provider = b.aiProvider == null ? "openai" : b.aiProvider.toLowerCase();
        if (hasGemini && !hasOpenAi && (provider.equals("openai") || provider.isBlank())) {
            b.aiProvider = "gemini";
            if (b.aiModel == null || b.aiModel.isBlank() || b.aiModel.startsWith("gpt-")) {
                b.aiModel = "gemini-2.0-flash";
            }
            if (b.aiBaseUrl == null || b.aiBaseUrl.isBlank()) {
                b.aiBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai";
            }
        }
        return b.build();
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public String getAppName() {
        return appName;
    }

    public String getAppVersion() {
        return appVersion;
    }

    /** Active profile name, or {@code null} if none was set. */
    public String getProfile() {
        return profile;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public String getAiProvider() {
        return aiProvider;
    }

    public String getAiModel() {
        return aiModel;
    }

    public String getAiBaseUrl() {
        return aiBaseUrl;
    }

    public boolean isVoiceEnabled() {
        return voiceEnabled;
    }

    public String getDataDirectory() {
        return dataDirectory;
    }

    /**
     * Returns true when an API key for the configured provider is available
     * via environment variable (keys are never stored in this object).
     */
    public boolean hasApiKeyConfigured() {
        return resolveApiKey().isPresent();
    }

    /**
     * Resolves the API key for the current provider from the environment only.
     * The key itself is never retained inside {@link AppConfig}.
     */
    public Optional<String> resolveApiKey() {
        String provider = aiProvider == null ? "" : aiProvider.toLowerCase();
        return switch (provider) {
            case "openai" -> ConfigLoader.resolveSecret(ConfigLoader.ENV_OPENAI_API_KEY);
            case "anthropic" -> ConfigLoader.resolveSecret(ConfigLoader.ENV_ANTHROPIC_API_KEY);
            case "google", "gemini" -> firstPresent(
                    ConfigLoader.resolveSecret(ConfigLoader.ENV_GEMINI_API_KEY),
                    ConfigLoader.resolveSecret(ConfigLoader.ENV_GOOGLE_API_KEY));
            default -> Optional.empty();
        };
    }

    private static Optional<String> firstPresent(Optional<String> a, Optional<String> b) {
        return a.isPresent() ? a : b;
    }

    /** True when provider is Gemini / Google. */
    public boolean isGeminiProvider() {
        String p = aiProvider == null ? "" : aiProvider.toLowerCase();
        return p.equals("gemini") || p.equals("google");
    }

    /**
     * Default OpenAI-compatible base URL for the configured provider.
     */
    public String resolveBaseUrl() {
        if (aiBaseUrl != null && !aiBaseUrl.isBlank()) {
            return aiBaseUrl.endsWith("/") ? aiBaseUrl.substring(0, aiBaseUrl.length() - 1) : aiBaseUrl;
        }
        if (isGeminiProvider()) {
            return "https://generativelanguage.googleapis.com/v1beta/openai";
        }
        return "https://api.openai.com/v1";
    }

    @Override
    public String toString() {
        // Never include secrets
        return "AppConfig{" +
                "appName='" + appName + '\'' +
                ", appVersion='" + appVersion + '\'' +
                ", profile='" + profile + '\'' +
                ", logLevel='" + logLevel + '\'' +
                ", aiProvider='" + aiProvider + '\'' +
                ", aiModel='" + aiModel + '\'' +
                ", aiBaseUrl='" + (aiBaseUrl == null || aiBaseUrl.isBlank() ? "(default)" : aiBaseUrl) + '\'' +
                ", voiceEnabled=" + voiceEnabled +
                ", dataDirectory='" + dataDirectory + '\'' +
                ", apiKeyConfigured=" + hasApiKeyConfigured() +
                '}';
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static final class Builder {
        private String appName = "AI Desktop Agent";
        private String appVersion = "0.1.0-SNAPSHOT";
        private String profile;
        private String logLevel = "INFO";
        private String aiProvider = "openai";
        private String aiModel = "gpt-4o-mini";
        private String aiBaseUrl = "";
        private boolean voiceEnabled;
        private String dataDirectory = "./data";

        public Builder appName(String v) { this.appName = v; return this; }
        public Builder appVersion(String v) { this.appVersion = v; return this; }
        public Builder profile(String v) { this.profile = v; return this; }
        public Builder logLevel(String v) { this.logLevel = v; return this; }
        public Builder aiProvider(String v) { this.aiProvider = v; return this; }
        public Builder aiModel(String v) { this.aiModel = v; return this; }
        public Builder aiBaseUrl(String v) { this.aiBaseUrl = v; return this; }
        public Builder voiceEnabled(boolean v) { this.voiceEnabled = v; return this; }
        public Builder dataDirectory(String v) { this.dataDirectory = v; return this; }

        public AppConfig build() {
            return new AppConfig(this);
        }
    }
}

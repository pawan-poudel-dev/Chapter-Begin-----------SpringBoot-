package com.aidesktopagent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * Loads configuration from multiple sources with a clear precedence order:
 * <ol>
 *   <li>Environment variables (highest priority)</li>
 *   <li>External config file (if path provided or {@code CONFIG_FILE} env set)</li>
 *   <li>Profile-specific classpath resource (e.g. {@code application-dev.properties})</li>
 *   <li>Base classpath resource {@code application.properties}</li>
 *   <li>Built-in defaults (lowest priority)</li>
 * </ol>
 *
 * <p>API keys and secrets are <strong>never</strong> read from properties files.
 * They are resolved only from environment variables (or system properties as fallback).
 */
public final class ConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(ConfigLoader.class);

    public static final String ENV_PROFILE = "APP_PROFILE";
    public static final String ENV_CONFIG_FILE = "CONFIG_FILE";
    public static final String ENV_LOG_LEVEL = "LOG_LEVEL";
    public static final String ENV_OPENAI_API_KEY = "OPENAI_API_KEY";
    public static final String ENV_ANTHROPIC_API_KEY = "ANTHROPIC_API_KEY";
    public static final String ENV_GOOGLE_API_KEY = "GOOGLE_API_KEY";
    public static final String ENV_GEMINI_API_KEY = "GEMINI_API_KEY";
    public static final String ENV_AI_PROVIDER = "AI_PROVIDER";
    public static final String ENV_AI_MODEL = "AI_MODEL";
    public static final String ENV_AI_BASE_URL = "AI_BASE_URL";

    private static final String BASE_RESOURCE = "application.properties";

    private ConfigLoader() {
        // utility
    }

    /**
     * Loads configuration using the active profile (from env or system property {@code app.profile}).
     */
    public static AppConfig load() {
        String profile = resolveProfile();
        return load(profile, null);
    }

    /**
     * Loads configuration for an explicit profile.
     *
     * @param profile active profile name (e.g. {@code dev}, {@code test}, {@code prod}); may be null/blank
     * @param externalFile optional path to an external properties file; may be null
     */
    public static AppConfig load(String profile, Path externalFile) {
        Properties merged = new Properties();

        // 1. Base classpath defaults
        loadClasspathResource(merged, BASE_RESOURCE);

        // 2. Profile-specific classpath resource
        if (profile != null && !profile.isBlank()) {
            String profileResource = "application-" + profile.trim().toLowerCase(Locale.ROOT) + ".properties";
            loadClasspathResource(merged, profileResource);
        }

        // 3. External file (explicit path or CONFIG_FILE env)
        Path fileToLoad = externalFile;
        if (fileToLoad == null) {
            String envPath = firstNonBlank(System.getenv(ENV_CONFIG_FILE), System.getProperty("config.file"));
            if (envPath != null) {
                fileToLoad = Path.of(envPath);
            }
        }
        if (fileToLoad != null) {
            loadExternalFile(merged, fileToLoad);
        }

        // 4. Environment / system-property overrides for non-secret settings
        applyEnvOverrides(merged);

        return AppConfig.fromProperties(merged, profile);
    }

    /**
     * Resolves an API key solely from the environment (or system property).
     * Never reads keys from configuration files.
     *
     * @param envName environment variable name (e.g. {@code OPENAI_API_KEY})
     * @return the key if present and non-blank, otherwise empty
     */
    public static Optional<String> resolveSecret(String envName) {
        Objects.requireNonNull(envName, "envName");
        String value = firstNonBlank(System.getenv(envName), System.getProperty(envName));
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.trim());
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    static String resolveProfile() {
        String fromEnv = firstNonBlank(
                System.getenv(ENV_PROFILE),
                System.getProperty("app.profile"),
                System.getProperty("spring.profiles.active")
        );
        return fromEnv != null ? fromEnv.trim().toLowerCase(Locale.ROOT) : null;
    }

    private static void loadClasspathResource(Properties target, String resourceName) {
        try (InputStream in = ConfigLoader.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                log.debug("Classpath resource '{}' not found (skipped)", resourceName);
                return;
            }
            Properties p = new Properties();
            p.load(in);
            target.putAll(p);
            log.debug("Loaded classpath configuration: {}", resourceName);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load classpath resource: " + resourceName, e);
        }
    }

    private static void loadExternalFile(Properties target, Path path) {
        if (!Files.isRegularFile(path)) {
            log.warn("External config file does not exist or is not a file: {}", path);
            return;
        }
        try (InputStream in = Files.newInputStream(path)) {
            Properties p = new Properties();
            p.load(in);
            // Defensive: strip any accidental secret keys from file-based config
            stripSecrets(p);
            target.putAll(p);
            log.info("Loaded external configuration from {}", path.toAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load external config: " + path, e);
        }
    }

    private static void applyEnvOverrides(Properties props) {
        // Non-secret overrides only
        putIfPresent(props, "logging.level", firstNonBlank(System.getenv(ENV_LOG_LEVEL), System.getProperty("logging.level")));
        putIfPresent(props, "app.name", firstNonBlank(System.getenv("APP_NAME"), System.getProperty("app.name")));
        putIfPresent(props, "app.version", firstNonBlank(System.getenv("APP_VERSION"), System.getProperty("app.version")));
        putIfPresent(props, "ai.provider", firstNonBlank(System.getenv(ENV_AI_PROVIDER), System.getProperty("ai.provider")));
        putIfPresent(props, "ai.model", firstNonBlank(System.getenv(ENV_AI_MODEL), System.getProperty("ai.model")));
        putIfPresent(props, "ai.base-url", firstNonBlank(System.getenv(ENV_AI_BASE_URL), System.getProperty("ai.base-url")));
    }

    private static void stripSecrets(Properties p) {
        // Never allow secret material from property files into the runtime config.
        p.remove("openai.api.key");
        p.remove("anthropic.api.key");
        p.remove("google.api.key");
        p.remove("api.key");
        p.remove("password");
        p.remove("secret");
    }

    private static void putIfPresent(Properties props, String key, String value) {
        if (value != null && !value.isBlank()) {
            props.setProperty(key, value.trim());
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}

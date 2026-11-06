package com.aidesktopagent.music;

import com.aidesktopagent.config.ConfigLoader;

import java.util.Optional;

/**
 * Spotify credentials from environment only — never from source or property files.
 *
 * <pre>
 * SPOTIFY_CLIENT_ID=...
 * SPOTIFY_CLIENT_SECRET=...
 * SPOTIFY_ACCESS_TOKEN=...      # short-lived; optional if refresh is set
 * SPOTIFY_REFRESH_TOKEN=...     # from Authorization Code flow
 * </pre>
 *
 * <p>Playback control requires a Spotify Premium account and an active device
 * (desktop app, web player, or Connect device). Free accounts cannot use the
 * Web API player endpoints.
 */
public final class SpotifyAuthConfig {

    public static final String ENV_CLIENT_ID = "SPOTIFY_CLIENT_ID";
    public static final String ENV_CLIENT_SECRET = "SPOTIFY_CLIENT_SECRET";
    public static final String ENV_ACCESS_TOKEN = "SPOTIFY_ACCESS_TOKEN";
    public static final String ENV_REFRESH_TOKEN = "SPOTIFY_REFRESH_TOKEN";

    private SpotifyAuthConfig() {
    }

    public static Optional<String> clientId() {
        return ConfigLoader.resolveSecret(ENV_CLIENT_ID);
    }

    public static Optional<String> clientSecret() {
        return ConfigLoader.resolveSecret(ENV_CLIENT_SECRET);
    }

    public static Optional<String> accessToken() {
        return ConfigLoader.resolveSecret(ENV_ACCESS_TOKEN);
    }

    public static Optional<String> refreshToken() {
        return ConfigLoader.resolveSecret(ENV_REFRESH_TOKEN);
    }

    public static boolean hasAccessToken() {
        return accessToken().isPresent();
    }

    public static boolean canAttemptApi() {
        return hasAccessToken() || (clientId().isPresent() && refreshToken().isPresent());
    }
}

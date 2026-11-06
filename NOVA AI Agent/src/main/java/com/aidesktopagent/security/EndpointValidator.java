package com.aidesktopagent.security;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Validates AI / STT base URLs before credentials are attached.
 */
public final class EndpointValidator {

    private static final Set<String> LOCAL_HOSTS = Set.of(
            "localhost", "127.0.0.1", "[::1]", "0.0.0.0");

    private EndpointValidator() {
    }

    /**
     * @param allowLocalHttp if true, http://localhost is permitted for dev
     */
    public static URI validateRemoteEndpoint(String raw, boolean allowLocalHttp) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Endpoint URL is required");
        }
        final URI uri;
        try {
            uri = URI.create(raw.trim()).normalize();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid endpoint URL: " + e.getMessage());
        }
        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
        String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";

        if (uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("Endpoint must not embed username/password");
        }
        if (host.isBlank()) {
            throw new IllegalArgumentException("Endpoint must include a host");
        }

        boolean local = LOCAL_HOSTS.contains(host);
        if ("https".equals(scheme)) {
            return uri;
        }
        if ("http".equals(scheme) && allowLocalHttp && local) {
            return uri;
        }
        if ("http".equals(scheme) && !local) {
            throw new IllegalArgumentException("Remote AI endpoints must use HTTPS");
        }
        throw new IllegalArgumentException("Unsupported endpoint scheme: " + scheme);
    }

    public static URI validateRemoteEndpoint(String raw) {
        return validateRemoteEndpoint(raw, true);
    }
}

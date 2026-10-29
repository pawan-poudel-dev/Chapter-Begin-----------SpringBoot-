package com.aidesktopagent.browser;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Allows only safe schemes for ordinary browsing.
 */
public final class UrlValidator {

    private static final Set<String> ALLOWED = Set.of("http", "https");

    private UrlValidator() {
    }

    public static URI requireHttpUrl(String raw) throws BrowserException {
        if (raw == null || raw.isBlank()) {
            throw new BrowserException("Empty URL");
        }
        String s = raw.trim();
        // Block obvious dangerous schemes early
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.startsWith("javascript:")
                || lower.startsWith("data:")
                || lower.startsWith("file:")
                || lower.startsWith("vbscript:")) {
            throw new BrowserException("Scheme not allowed: " + schemeOf(lower));
        }
        if (!s.contains("://")) {
            s = "https://" + s;
        }
        final URI uri;
        try {
            uri = URI.create(s);
        } catch (Exception e) {
            throw new BrowserException("Invalid URL: " + e.getMessage(), e);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED.contains(scheme.toLowerCase(Locale.ROOT))) {
            throw new BrowserException("Only http/https URLs are allowed (got: " + scheme + ")");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new BrowserException("URL must include a host");
        }
        return uri;
    }

    private static String schemeOf(String lower) {
        int i = lower.indexOf(':');
        return i > 0 ? lower.substring(0, i) : lower;
    }
}

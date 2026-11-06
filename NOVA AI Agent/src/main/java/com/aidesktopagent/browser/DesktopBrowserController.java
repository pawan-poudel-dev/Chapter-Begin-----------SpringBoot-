package com.aidesktopagent.browser;

import com.aidesktopagent.logging.LoggingService;

import java.awt.Desktop;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Default browser controller using {@link java.awt.Desktop#browse(URI)}.
 * Does not automate page content or click through ads/consent dialogs.
 */
public final class DesktopBrowserController implements BrowserController {

    private final LoggingService log;

    public DesktopBrowserController() {
        this(LoggingService.forComponent("BrowserController"));
    }

    public DesktopBrowserController(LoggingService log) {
        this.log = log != null ? log : LoggingService.forComponent("BrowserController");
    }

    @Override
    public boolean isAvailable() {
        return Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE);
    }

    @Override
    public String openUrl(URI uri) throws BrowserException {
        if (uri == null) {
            throw new BrowserException("URI is null");
        }
        // Re-validate scheme
        UrlValidator.requireHttpUrl(uri.toString());
        if (!isAvailable()) {
            throw new BrowserException("Desktop browse is not supported on this system");
        }
        try {
            Desktop.getDesktop().browse(uri);
            log.info("Opened browser URL: " + uri.getHost() + uri.getPath());
            return "Opened " + uri;
        } catch (Exception e) {
            throw new BrowserException("Failed to open browser: " + e.getMessage(), e);
        }
    }

    @Override
    public String openUrl(String rawUrl) throws BrowserException {
        return openUrl(UrlValidator.requireHttpUrl(rawUrl));
    }

    @Override
    public String searchWeb(String query) throws BrowserException {
        if (query == null || query.isBlank()) {
            throw new BrowserException("Empty search query");
        }
        String q = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
        URI uri = URI.create("https://www.google.com/search?q=" + q);
        return openUrl(uri);
    }
}

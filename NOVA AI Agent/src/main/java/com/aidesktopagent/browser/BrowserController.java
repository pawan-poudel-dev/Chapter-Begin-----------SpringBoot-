package com.aidesktopagent.browser;

import java.net.URI;

/**
 * Opens and navigates the system browser. Only http/https for ordinary browsing.
 */
public interface BrowserController {

    /**
     * Open a validated http(s) URL in the default browser.
     *
     * @return human-readable result message
     * @throws BrowserException on validation or OS failure
     */
    String openUrl(URI uri) throws BrowserException;

    String openUrl(String rawUrl) throws BrowserException;

    String searchWeb(String query) throws BrowserException;

    /** Whether the Desktop browse action is available. */
    boolean isAvailable();
}

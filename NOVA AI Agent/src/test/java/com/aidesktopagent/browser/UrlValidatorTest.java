package com.aidesktopagent.browser;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class UrlValidatorTest {

    @Test
    void acceptsHttps() throws Exception {
        URI u = UrlValidator.requireHttpUrl("https://example.com/path");
        assertEquals("example.com", u.getHost());
    }

    @Test
    void addsHttpsWhenMissing() throws Exception {
        URI u = UrlValidator.requireHttpUrl("example.com");
        assertEquals("https", u.getScheme());
    }

    @Test
    void rejectsJavascript() {
        assertThrows(BrowserException.class, () -> UrlValidator.requireHttpUrl("javascript:alert(1)"));
    }

    @Test
    void rejectsFileScheme() {
        assertThrows(BrowserException.class, () -> UrlValidator.requireHttpUrl("file:///etc/passwd"));
    }

    @Test
    void rejectsDataScheme() {
        assertThrows(BrowserException.class, () -> UrlValidator.requireHttpUrl("data:text/html,hi"));
    }

    @Test
    void rejectsEmpty() {
        assertThrows(BrowserException.class, () -> UrlValidator.requireHttpUrl("  "));
    }
}

package com.aidesktopagent.music;

import com.aidesktopagent.browser.BrowserController;
import com.aidesktopagent.browser.BrowserException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpotifyControllerTest {

    private RecordingBrowser browser;
    private MediaSessionContext session;
    private SpotifyController spotify;

    @BeforeEach
    void setUp() {
        browser = new RecordingBrowser();
        session = new MediaSessionContext();
        // No access token → auth required / browser fallback
        spotify = new SpotifyController(browser, session,
                HttpClient.newHttpClient(), new ObjectMapper(), null, Duration.ofSeconds(5), null);
    }

    @Test
    void openWithoutAuthOpensWeb() {
        MediaStatus s = spotify.openAppOrWeb();
        assertEquals(MediaStatus.Outcome.UNKNOWN, s.getOutcome());
        assertTrue(browser.opened.get(0).contains("open.spotify.com"));
    }

    @Test
    void playWithoutAuthReportsNeedsAuth() {
        MediaStatus s = spotify.play("relaxing music");
        assertEquals(MediaStatus.Outcome.NEEDS_AUTH, s.getOutcome());
        assertTrue(s.getMessage().toLowerCase().contains("not authenticated")
                || s.getMessage().toLowerCase().contains("spotify"));
        assertTrue(browser.opened.stream().anyMatch(u -> u.contains("search")));
    }

    @Test
    void pauseWithoutAuthNeedsAuth() {
        MediaStatus s = spotify.pause();
        assertEquals(MediaStatus.Outcome.NEEDS_AUTH, s.getOutcome());
    }

    @Test
    void emptyPlayFails() {
        assertEquals(MediaStatus.Outcome.FAILURE, spotify.play("  ").getOutcome());
    }

    @Test
    void statusWithoutTokenNeedsAuth() {
        assertEquals(MediaStatus.Outcome.NEEDS_AUTH, spotify.status().getOutcome());
    }

    static final class RecordingBrowser implements BrowserController {
        final List<String> opened = new ArrayList<>();

        @Override
        public String openUrl(URI uri) {
            opened.add(uri.toString());
            return "ok";
        }

        @Override
        public String openUrl(String rawUrl) {
            opened.add(rawUrl);
            return "ok";
        }

        @Override
        public String searchWeb(String query) throws BrowserException {
            return openUrl("https://www.google.com/search?q=" + query);
        }

        @Override
        public boolean isAvailable() {
            return true;
        }
    }
}

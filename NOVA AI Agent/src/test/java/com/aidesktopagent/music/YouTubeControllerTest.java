package com.aidesktopagent.music;

import com.aidesktopagent.browser.BrowserController;
import com.aidesktopagent.browser.BrowserException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class YouTubeControllerTest {

    private RecordingBrowser browser;
    private MediaSessionContext session;
    private YouTubeController yt;

    @BeforeEach
    void setUp() {
        browser = new RecordingBrowser();
        session = new MediaSessionContext();
        yt = new YouTubeController(browser, session);
    }

    @Test
    void openHome() {
        MediaStatus s = yt.openHome();
        assertEquals(MediaStatus.Outcome.UNKNOWN, s.getOutcome());
        assertTrue(browser.opened.stream().anyMatch(u -> u.contains("youtube.com")));
        assertFalse(s.isVerifiedSuccess()); // must not claim verified playback
    }

    @Test
    void searchBuildsResultsUrl() {
        MediaStatus s = yt.search("Java tutorial");
        assertEquals(MediaStatus.Outcome.UNKNOWN, s.getOutcome());
        assertTrue(browser.opened.get(0).contains("search_query="));
        assertEquals("Java tutorial", session.getLastQuery().orElseThrow());
    }

    @Test
    void playWithVideoId() {
        MediaStatus s = yt.play("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        assertTrue(browser.opened.get(0).contains("watch?v=dQw4w9WgXcQ"));
        assertTrue(s.getMessage().toLowerCase().contains("couldn't verify")
                || s.getMessage().toLowerCase().contains("not verify"));
    }

    @Test
    void playQueryFallsBackToSearch() {
        yt.play("machine learning");
        assertTrue(browser.opened.get(0).contains("results"));
    }

    @Test
    void missingQueryFails() {
        assertEquals(MediaStatus.Outcome.FAILURE, yt.search("").getOutcome());
    }

    static final class RecordingBrowser implements BrowserController {
        final List<String> opened = new ArrayList<>();

        @Override
        public String openUrl(URI uri) {
            opened.add(uri.toString());
            return "Opened " + uri;
        }

        @Override
        public String openUrl(String rawUrl) throws BrowserException {
            opened.add(rawUrl);
            return "Opened " + rawUrl;
        }

        @Override
        public String searchWeb(String query) {
            return openUrl(URI.create("https://www.google.com/search?q=" + query));
        }

        @Override
        public boolean isAvailable() {
            return true;
        }
    }
}

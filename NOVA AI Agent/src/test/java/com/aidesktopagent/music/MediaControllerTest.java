package com.aidesktopagent.music;

import com.aidesktopagent.actions.Action;
import com.aidesktopagent.actions.ActionType;
import com.aidesktopagent.browser.BrowserController;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MediaControllerTest {

    @Test
    void playYoutubeAction() {
        RecordingBrowser browser = new RecordingBrowser();
        MediaController media = new MediaController(browser);
        MediaStatus s = media.handle(Action.builder(ActionType.PLAY_YOUTUBE)
                .target("relaxing music").build());
        assertEquals(MediaStatus.Source.YOUTUBE, s.getSource());
        assertFalse(browser.opened.isEmpty());
    }

    @Test
    void openSpotifyAction() {
        RecordingBrowser browser = new RecordingBrowser();
        MediaController media = new MediaController(browser);
        MediaStatus s = media.handle(Action.builder(ActionType.OPEN_SPOTIFY).build());
        assertEquals(MediaStatus.Source.SPOTIFY, s.getSource());
    }

    @Test
    void pauseWithoutSessionPrefersSpotifyAuthMessage() {
        RecordingBrowser browser = new RecordingBrowser();
        MediaController media = new MediaController(browser);
        MediaStatus s = media.handle(Action.builder(ActionType.PAUSE_MEDIA).build());
        // No token → needs auth from Spotify path
        assertTrue(s.getOutcome() == MediaStatus.Outcome.NEEDS_AUTH
                || s.getOutcome() == MediaStatus.Outcome.NEEDS_USER_ACTION);
    }

    @Test
    void sessionRemembersYoutubeForNext() {
        RecordingBrowser browser = new RecordingBrowser();
        MediaSessionContext session = new MediaSessionContext();
        YouTubeController yt = new YouTubeController(browser, session);
        SpotifyController sp = new SpotifyController(browser, session);
        MediaController media = new MediaController(yt, sp, session);

        media.handle(Action.builder(ActionType.SEARCH_YOUTUBE).target("Java tutorial").build());
        MediaStatus next = media.handle(Action.builder(ActionType.NEXT_TRACK).build());
        assertEquals(MediaStatus.Source.YOUTUBE, next.getSource());
        assertEquals(MediaStatus.Outcome.NEEDS_USER_ACTION, next.getOutcome());
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
        public String searchWeb(String query) {
            return "ok";
        }

        @Override
        public boolean isAvailable() {
            return true;
        }
    }
}

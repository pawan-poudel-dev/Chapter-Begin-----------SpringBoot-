package com.aidesktopagent.music;

import com.aidesktopagent.browser.BrowserController;
import com.aidesktopagent.browser.BrowserException;
import com.aidesktopagent.logging.LoggingService;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * YouTube control via the system browser (search / open URLs).
 *
 * <p>Does <strong>not</strong> claim that video playback has started — Desktop.browse
 * only opens a page. Ads, login, consent, or autoplay policies may block playback.
 * Optional YouTube Data API is not required for open/search.
 */
public final class YouTubeController {

    private static final Pattern VIDEO_ID = Pattern.compile(
            "(?:youtu\\.be/|v=|embed/|shorts/)([A-Za-z0-9_-]{6,})");

    private final BrowserController browser;
    private final MediaSessionContext session;
    private final LoggingService log;

    public YouTubeController(BrowserController browser, MediaSessionContext session) {
        this(browser, session, LoggingService.forComponent("YouTube"));
    }

    public YouTubeController(BrowserController browser, MediaSessionContext session, LoggingService log) {
        this.browser = Objects.requireNonNull(browser);
        this.session = session != null ? session : new MediaSessionContext();
        this.log = log != null ? log : LoggingService.forComponent("YouTube");
    }

    public MediaStatus openHome() {
        try {
            browser.openUrl("https://www.youtube.com");
            session.rememberYoutube("home");
            return MediaStatus.opened(MediaStatus.Source.YOUTUBE,
                    "Opened YouTube. Playback was not verified.");
        } catch (BrowserException e) {
            return MediaStatus.failure(MediaStatus.Source.YOUTUBE, e.getMessage());
        }
    }

    public MediaStatus search(String query) {
        if (query == null || query.isBlank()) {
            return MediaStatus.failure(MediaStatus.Source.YOUTUBE, "Missing search query");
        }
        try {
            String q = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
            URI uri = URI.create("https://www.youtube.com/results?search_query=" + q);
            browser.openUrl(uri);
            session.rememberYoutube(query.trim());
            return MediaStatus.opened(MediaStatus.Source.YOUTUBE,
                    "Opened YouTube search for \"" + query.trim()
                            + "\". Select a video if playback does not start automatically.");
        } catch (BrowserException e) {
            return MediaStatus.failure(MediaStatus.Source.YOUTUBE, e.getMessage());
        }
    }

    /**
     * Open a specific video URL or treat target as a search query.
     */
    public MediaStatus play(String target) {
        if (target == null || target.isBlank()) {
            return MediaStatus.failure(MediaStatus.Source.YOUTUBE, "Missing video or query");
        }
        String t = target.trim();
        String lower = t.toLowerCase(Locale.ROOT);
        try {
            if (lower.contains("youtube.com") || lower.contains("youtu.be")) {
                String watch = normalizeWatchUrl(t);
                browser.openUrl(watch);
                session.rememberYoutube(watch);
                return MediaStatus.opened(MediaStatus.Source.YOUTUBE,
                        "Opened the video. I couldn't verify whether playback started "
                                + "(ads, login, or autoplay settings may apply).");
            }
            return search(t);
        } catch (BrowserException e) {
            return MediaStatus.failure(MediaStatus.Source.YOUTUBE, e.getMessage());
        }
    }

    public MediaStatus openNextFromSession() {
        // Without page automation we can only re-open search results
        return session.getLastQuery()
                .map(q -> {
                    if ("home".equalsIgnoreCase(q)) {
                        return openHome();
                    }
                    MediaStatus s = search(q);
                    return new MediaStatus(MediaStatus.Outcome.NEEDS_USER_ACTION,
                            MediaStatus.Source.YOUTUBE,
                            "Re-opened search results for \"" + q
                                    + "\". Choose the next video in the browser — "
                                    + "automatic next-track is not available without browser automation.",
                            "needs_user_selection", false);
                })
                .orElse(MediaStatus.needsUserAction(MediaStatus.Source.YOUTUBE,
                        "No recent YouTube search in this session. Say what to play."));
    }

    /**
     * Pause/resume via browser is not reliably available without automation.
     */
    public MediaStatus pauseOrResumeUnsupported(String verb) {
        return MediaStatus.needsUserAction(MediaStatus.Source.YOUTUBE,
                "I can't " + verb + " YouTube from here without browser automation. "
                        + "Use the player controls or spacebar in the browser tab.");
    }

    static String normalizeWatchUrl(String raw) {
        Matcher m = VIDEO_ID.matcher(raw);
        if (m.find()) {
            return "https://www.youtube.com/watch?v=" + m.group(1);
        }
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            return raw;
        }
        return "https://www.youtube.com/results?search_query="
                + URLEncoder.encode(raw, StandardCharsets.UTF_8);
    }
}

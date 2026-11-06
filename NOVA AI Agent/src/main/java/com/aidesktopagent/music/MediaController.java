package com.aidesktopagent.music;

import com.aidesktopagent.actions.Action;
import com.aidesktopagent.actions.ActionType;
import com.aidesktopagent.browser.BrowserController;
import com.aidesktopagent.logging.LoggingService;

import java.util.Objects;

/**
 * Facade over YouTube + Spotify + session context for media actions.
 */
public final class MediaController {

    private final YouTubeController youtube;
    private final SpotifyController spotify;
    private final MediaSessionContext session;
    private final LoggingService log;

    public MediaController(BrowserController browser) {
        this(browser, new MediaSessionContext(), LoggingService.forComponent("Media"));
    }

    public MediaController(BrowserController browser, MediaSessionContext session, LoggingService log) {
        this.session = session != null ? session : new MediaSessionContext();
        this.youtube = new YouTubeController(browser, this.session, log);
        this.spotify = new SpotifyController(browser, this.session);
        this.log = log != null ? log : LoggingService.forComponent("Media");
    }

    /** Test-friendly constructor. */
    public MediaController(YouTubeController youtube, SpotifyController spotify, MediaSessionContext session) {
        this.youtube = Objects.requireNonNull(youtube);
        this.spotify = Objects.requireNonNull(spotify);
        this.session = session != null ? session : new MediaSessionContext();
        this.log = LoggingService.forComponent("Media");
    }

    public MediaSessionContext getSession() {
        return session;
    }

    public MediaStatus handle(Action action) {
        ActionType type = action.getType();
        String target = action.getTarget().orElse("");
        log.info("Media action " + type + (target.isBlank() ? "" : " target=" + target));

        return switch (type) {
            case SEARCH_YOUTUBE -> youtube.search(target);
            case OPEN_YOUTUBE_VIDEO, PLAY_YOUTUBE -> youtube.play(target);
            case OPEN_SPOTIFY -> {
                if (target.isBlank() || target.equalsIgnoreCase("spotify")) {
                    yield spotify.openAppOrWeb();
                }
                yield spotify.search(target);
            }
            case SEARCH_SPOTIFY -> spotify.search(target);
            case PLAY_SPOTIFY -> spotify.play(target);
            case PAUSE_MEDIA -> pause();
            case RESUME_MEDIA -> resume();
            case STOP_MEDIA -> pause(); // best-effort
            case NEXT_TRACK -> next();
            case PREVIOUS_TRACK -> previous();
            case GET_MEDIA_STATUS -> status();
            default -> MediaStatus.failure(MediaStatus.Source.NONE, "Not a media action: " + type);
        };
    }

    private MediaStatus pause() {
        return switch (session.getLastKind()) {
            case SPOTIFY -> spotify.pause();
            case YOUTUBE -> youtube.pauseOrResumeUnsupported("pause");
            default -> preferSpotifyThenYoutube(
                    spotify.pause(),
                    youtube.pauseOrResumeUnsupported("pause"));
        };
    }

    private MediaStatus resume() {
        return switch (session.getLastKind()) {
            case SPOTIFY -> spotify.resume();
            case YOUTUBE -> youtube.pauseOrResumeUnsupported("resume");
            default -> preferSpotifyThenYoutube(
                    spotify.resume(),
                    youtube.pauseOrResumeUnsupported("resume"));
        };
    }

    private MediaStatus next() {
        return switch (session.getLastKind()) {
            case SPOTIFY -> spotify.next();
            case YOUTUBE -> youtube.openNextFromSession();
            default -> preferSpotifyThenYoutube(spotify.next(), youtube.openNextFromSession());
        };
    }

    private MediaStatus previous() {
        if (session.getLastKind() == MediaSessionContext.LastKind.SPOTIFY
                || session.getLastKind() == MediaSessionContext.LastKind.NONE) {
            return spotify.previous();
        }
        return MediaStatus.needsUserAction(MediaStatus.Source.YOUTUBE,
                "Previous track on YouTube isn't automated. Use the browser player controls.");
    }

    private MediaStatus status() {
        if (session.getLastKind() == MediaSessionContext.LastKind.SPOTIFY) {
            return spotify.status();
        }
        MediaStatus s = spotify.status();
        if (s.getOutcome() != MediaStatus.Outcome.NEEDS_AUTH) {
            return s;
        }
        return MediaStatus.opened(MediaStatus.Source.YOUTUBE,
                "Last media was YouTube/browser — playback status can't be read without page automation. "
                        + "Spotify status unavailable (not authenticated).");
    }

    private static MediaStatus preferSpotifyThenYoutube(MediaStatus spotifyResult, MediaStatus youtubeResult) {
        if (spotifyResult.getOutcome() != MediaStatus.Outcome.NEEDS_AUTH
                && spotifyResult.getOutcome() != MediaStatus.Outcome.NO_ACTIVE_DEVICE) {
            return spotifyResult;
        }
        // If Spotify needs auth, still return that message (more actionable) unless caller preferred YT
        if (spotifyResult.getOutcome() == MediaStatus.Outcome.NEEDS_AUTH) {
            return spotifyResult;
        }
        return youtubeResult;
    }
}

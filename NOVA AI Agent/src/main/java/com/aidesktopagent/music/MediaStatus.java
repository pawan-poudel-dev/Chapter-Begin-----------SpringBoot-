package com.aidesktopagent.music;

import java.util.Optional;

/**
 * Snapshot of media playback state after an action.
 */
public final class MediaStatus {

    public enum Outcome {
        SUCCESS,
        FAILURE,
        UNKNOWN,
        NEEDS_AUTH,
        NEEDS_USER_ACTION,
        NO_ACTIVE_DEVICE
    }

    public enum Source {
        NONE,
        YOUTUBE,
        SPOTIFY,
        BROWSER,
        SYSTEM
    }

    private final Outcome outcome;
    private final Source source;
    private final String message;
    private final String detail;
    private final boolean playing;

    public MediaStatus(Outcome outcome, Source source, String message, String detail, boolean playing) {
        this.outcome = outcome != null ? outcome : Outcome.UNKNOWN;
        this.source = source != null ? source : Source.NONE;
        this.message = message != null ? message : "";
        this.detail = detail;
        this.playing = playing;
    }

    public static MediaStatus success(Source source, String message) {
        return new MediaStatus(Outcome.SUCCESS, source, message, null, true);
    }

    public static MediaStatus opened(Source source, String message) {
        // Opened UI but playback not verified
        return new MediaStatus(Outcome.UNKNOWN, source, message, "playback_not_verified", false);
    }

    public static MediaStatus failure(Source source, String message) {
        return new MediaStatus(Outcome.FAILURE, source, message, null, false);
    }

    public static MediaStatus needsAuth(Source source, String message) {
        return new MediaStatus(Outcome.NEEDS_AUTH, source, message, null, false);
    }

    public static MediaStatus needsUserAction(Source source, String message) {
        return new MediaStatus(Outcome.NEEDS_USER_ACTION, source, message, null, false);
    }

    public static MediaStatus noDevice(Source source, String message) {
        return new MediaStatus(Outcome.NO_ACTIVE_DEVICE, source, message, null, false);
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public Source getSource() {
        return source;
    }

    public String getMessage() {
        return message;
    }

    public Optional<String> getDetail() {
        return Optional.ofNullable(detail);
    }

    public boolean isPlaying() {
        return playing;
    }

    public boolean isVerifiedSuccess() {
        return outcome == Outcome.SUCCESS;
    }

    @Override
    public String toString() {
        return "MediaStatus{outcome=" + outcome + ", source=" + source
                + ", playing=" + playing + ", message='" + message + "'}";
    }
}

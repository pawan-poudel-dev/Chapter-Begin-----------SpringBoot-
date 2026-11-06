package com.aidesktopagent.music;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Remembers the last media target in the session for phrases like
 * "pause that", "next track", "continue playing".
 */
public final class MediaSessionContext {

    public enum LastKind {
        NONE,
        YOUTUBE,
        SPOTIFY,
        WEB
    }

    private final AtomicReference<LastKind> lastKind = new AtomicReference<>(LastKind.NONE);
    private final AtomicReference<String> lastQuery = new AtomicReference<>();
    private final AtomicReference<String> lastUri = new AtomicReference<>();

    public void rememberYoutube(String queryOrUrl) {
        lastKind.set(LastKind.YOUTUBE);
        lastQuery.set(queryOrUrl);
        lastUri.set(null);
    }

    public void rememberSpotify(String queryOrUri) {
        lastKind.set(LastKind.SPOTIFY);
        lastQuery.set(queryOrUri);
        lastUri.set(queryOrUri);
    }

    public void rememberWeb(String url) {
        lastKind.set(LastKind.WEB);
        lastUri.set(url);
    }

    public LastKind getLastKind() {
        return lastKind.get();
    }

    public Optional<String> getLastQuery() {
        return Optional.ofNullable(lastQuery.get());
    }

    public Optional<String> getLastUri() {
        return Optional.ofNullable(lastUri.get());
    }

    public void clear() {
        lastKind.set(LastKind.NONE);
        lastQuery.set(null);
        lastUri.set(null);
    }
}

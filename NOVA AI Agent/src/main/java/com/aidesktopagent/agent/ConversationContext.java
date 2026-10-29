package com.aidesktopagent.agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Multi-turn context for references like "the second result" / "that file" (Day 17).
 * Context expires after a TTL and never grants permissions by itself.
 */
public final class ConversationContext {

    public record SearchHit(String pathOrUrl, String label) {
    }

    private final long ttlMillis;
    private volatile Instant lastUpdate = Instant.EPOCH;
    private final List<SearchHit> lastResults = new ArrayList<>();
    private volatile String lastFile;
    private volatile String lastCommand;
    private volatile String lastMediaProvider;

    public ConversationContext() {
        this(10 * 60_000L); // 10 minutes
    }

    public ConversationContext(long ttlMillis) {
        this.ttlMillis = ttlMillis > 0 ? ttlMillis : 600_000L;
    }

    private boolean alive() {
        return Instant.now().toEpochMilli() - lastUpdate.toEpochMilli() < ttlMillis;
    }

    private void touch() {
        lastUpdate = Instant.now();
    }

    public void setLastResults(List<SearchHit> hits) {
        synchronized (lastResults) {
            lastResults.clear();
            if (hits != null) {
                lastResults.addAll(hits);
            }
        }
        touch();
    }

    public void setLastFile(String path) {
        lastFile = path;
        touch();
    }

    public void setLastCommand(String cmd) {
        lastCommand = cmd;
        touch();
    }

    public void setLastMediaProvider(String provider) {
        lastMediaProvider = provider;
        touch();
    }

    public Optional<String> resolveReference(String utterance) {
        if (utterance == null || !alive()) {
            return Optional.empty();
        }
        String u = utterance.toLowerCase().trim();
        if (u.contains("that file") || u.equals("it") || u.contains("the file")) {
            return Optional.ofNullable(lastFile);
        }
        if (u.contains("second result") || u.contains("2nd result") || u.contains("result 2")) {
            return hit(1);
        }
        if (u.contains("first result") || u.contains("1st result") || u.contains("result 1")) {
            return hit(0);
        }
        if (u.contains("third result") || u.contains("3rd result")) {
            return hit(2);
        }
        return Optional.empty();
    }

    private Optional<String> hit(int index) {
        synchronized (lastResults) {
            if (index >= 0 && index < lastResults.size()) {
                return Optional.of(lastResults.get(index).pathOrUrl());
            }
        }
        return Optional.empty();
    }

    public Optional<String> getLastFile() {
        return alive() ? Optional.ofNullable(lastFile) : Optional.empty();
    }

    public Optional<String> getLastMediaProvider() {
        return alive() ? Optional.ofNullable(lastMediaProvider) : Optional.empty();
    }

    public void clear() {
        synchronized (lastResults) {
            lastResults.clear();
        }
        lastFile = null;
        lastCommand = null;
        lastMediaProvider = null;
        lastUpdate = Instant.EPOCH;
    }

    public boolean isAmbiguous(String utterance) {
        if (utterance == null) {
            return false;
        }
        String u = utterance.toLowerCase();
        return (u.contains("that") || u.contains("it ")) && resolveReference(utterance).isEmpty() && alive();
    }
}

package com.aidesktopagent.ai;

import java.util.Locale;
import java.util.Map;

/**
 * Synonym normalization and low-confidence detection (Day 16).
 */
public final class CommandNormalizer {

    private static final Map<String, String> SYNONYMS = Map.ofEntries(
            Map.entry("fire up", "open"),
            Map.entry("launch", "open"),
            Map.entry("start up", "open"),
            Map.entry("boot", "open"),
            Map.entry("make a folder", "create a folder"),
            Map.entry("mkdir", "create a folder"),
            Map.entry("stop the music", "pause"),
            Map.entry("halt", "pause"),
            Map.entry("keep going", "resume"),
            Map.entry("go on", "resume"),
            Map.entry("skip song", "next track"),
            Map.entry("whats playing", "media status")
    );

    public String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim().replaceAll("\\s+", " ");
        String lower = s.toLowerCase(Locale.ROOT);
        for (var e : SYNONYMS.entrySet()) {
            if (lower.startsWith(e.getKey())) {
                return e.getValue() + s.substring(e.getKey().length());
            }
            if (lower.equals(e.getKey())) {
                return e.getValue();
            }
        }
        return s;
    }

    /**
     * Heuristic: very short, only filler words, or trailing uncertainty markers.
     */
    public boolean isLowConfidence(String raw) {
        if (raw == null || raw.isBlank()) {
            return true;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.length() < 2) {
            return true;
        }
        if (s.equals("um") || s.equals("uh") || s.equals("er") || s.equals("hmm")) {
            return true;
        }
        if (s.endsWith("?") && s.split("\\s+").length <= 2) {
            return true;
        }
        return false;
    }

    public boolean isDestructiveAmbiguous(String raw) {
        if (raw == null) {
            return false;
        }
        String s = raw.toLowerCase(Locale.ROOT);
        return (s.contains("delete") || s.contains("remove") || s.contains("wipe"))
                && (s.contains("something") || s.contains("stuff") || s.contains("it")
                || s.contains("that") || s.trim().split("\\s+").length < 3);
    }
}

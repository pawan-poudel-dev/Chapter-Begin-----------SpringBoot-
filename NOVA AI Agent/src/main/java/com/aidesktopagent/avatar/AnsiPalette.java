package com.aidesktopagent.avatar;

/**
 * Vibrant ANSI colors for the robot companion console art.
 * Respects NO_COLOR / reduced-motion by returning plain text.
 */
public final class AnsiPalette {

    public static final String RESET = "\u001B[0m";
    public static final String BOLD = "\u001B[1m";
    public static final String DIM = "\u001B[2m";

    // Neon / wow palette
    public static final String CYAN = "\u001B[38;2;0;230;255m";
    public static final String MAGENTA = "\u001B[38;2;255;64;255m";
    public static final String VIOLET = "\u001B[38;2;180;100;255m";
    public static final String LIME = "\u001B[38;2;80;255;120m";
    public static final String GOLD = "\u001B[38;2;255;210;50m";
    public static final String CORAL = "\u001B[38;2;255;90;90m";
    public static final String SKY = "\u001B[38;2;120;180;255m";
    public static final String WHITE = "\u001B[38;2;240;248;255m";
    public static final String SOFT = "\u001B[38;2;160;170;200m";

    private final boolean enabled;

    public AnsiPalette(boolean enabled) {
        this.enabled = enabled;
    }

    public static AnsiPalette auto() {
        String noColor = System.getenv("NO_COLOR");
        String term = System.getenv("TERM");
        boolean dumb = term != null && term.equalsIgnoreCase("dumb");
        boolean force = "true".equalsIgnoreCase(System.getenv("AGENT_COLOR"))
                || "1".equals(System.getenv("AGENT_COLOR"));
        boolean off = (noColor != null && !noColor.isBlank()) || dumb;
        return new AnsiPalette(force || !off);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String paint(String color, String text) {
        if (!enabled) {
            return text;
        }
        return color + text + RESET;
    }

    public String bold(String color, String text) {
        if (!enabled) {
            return text;
        }
        return BOLD + color + text + RESET;
    }
}

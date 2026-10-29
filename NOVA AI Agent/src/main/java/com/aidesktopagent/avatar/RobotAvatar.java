package com.aidesktopagent.avatar;

import com.aidesktopagent.agent.AgentState;
import com.aidesktopagent.agent.AgentStateManager;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Original neon robot companion — cyan chassis, glowing eyes, mood-reactive colors.
 * Not based on any protected character design.
 */
public final class RobotAvatar {

    private final CopyOnWriteArrayList<Consumer<AvatarMood>> listeners = new CopyOnWriteArrayList<>();
    private volatile AvatarMood mood = AvatarMood.IDLE;
    private final boolean reducedMotion;
    private final AnsiPalette colors;

    public RobotAvatar() {
        this(false, AnsiPalette.auto());
    }

    public RobotAvatar(boolean reducedMotion) {
        this(reducedMotion, reducedMotion ? new AnsiPalette(false) : AnsiPalette.auto());
    }

    public RobotAvatar(boolean reducedMotion, AnsiPalette colors) {
        this.reducedMotion = reducedMotion;
        this.colors = colors != null ? colors : AnsiPalette.auto();
    }

    public AvatarMood getMood() {
        return mood;
    }

    public void setMood(AvatarMood next) {
        Objects.requireNonNull(next);
        if (mood == next) {
            return;
        }
        mood = next;
        for (Consumer<AvatarMood> l : listeners) {
            try {
                l.accept(next);
            } catch (Exception ignored) {
            }
        }
    }

    public void bindToAgentState(AgentStateManager sm) {
        sm.addListener(this::onAgentState);
    }

    private void onAgentState(AgentState s) {
        setMood(switch (s) {
            case IDLE -> AvatarMood.IDLE;
            case LISTENING, TRANSCRIBING -> AvatarMood.LISTENING;
            case THINKING, VALIDATING -> AvatarMood.THINKING;
            case EXECUTING, WAITING_FOR_CONFIRMATION -> AvatarMood.EXECUTING;
            case SUCCESS -> AvatarMood.SUCCESS;
            case ERROR -> AvatarMood.ERROR;
            case STOPPED -> AvatarMood.SLEEPING;
        });
    }

    public void addListener(Consumer<AvatarMood> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public boolean isReducedMotion() {
        return reducedMotion;
    }

    public AnsiPalette getColors() {
        return colors;
    }

    public String renderFrame() {
        return switch (mood) {
            case IDLE -> frame(
                    AnsiPalette.CYAN, AnsiPalette.SKY,
                    "  (•‿•)  ", " Ready when you are ",
                    "  /|\\    ", " soft cyan glow ");
            case LISTENING -> frame(
                    AnsiPalette.MAGENTA, AnsiPalette.VIOLET,
                    "  (◉‿◉)  ", " Listening… ",
                    "  /|\\  ♪ ", " magenta antennas live ");
            case THINKING -> frame(
                    AnsiPalette.VIOLET, AnsiPalette.GOLD,
                    "  (・・)? ", " Thinking… ",
                    "  /|\\  ✦ ", " violet circuits whir ");
            case EXECUTING -> frame(
                    AnsiPalette.GOLD, AnsiPalette.LIME,
                    "  (•̀ᴗ•́) ", " Working… ",
                    "  /|\\ ⚡  ", " gold power core online ");
            case SUCCESS -> frame(
                    AnsiPalette.LIME, AnsiPalette.CYAN,
                    "  (＾▽＾) ", " Done! ",
                    "  /|\\ ★  ", " lime victory lights ");
            case ERROR -> frame(
                    AnsiPalette.CORAL, AnsiPalette.GOLD,
                    "  (×_×)  ", " Oops — need a hand ",
                    "  /|\\    ", " coral alert ");
            case SLEEPING -> frame(
                    AnsiPalette.SOFT, AnsiPalette.SKY,
                    "  (-_-)zz", " Resting ",
                    "  /|\\    ", " dim standby ");
        };
    }

    public String renderBadge() {
        String label = switch (mood) {
            case IDLE -> "IDLE";
            case LISTENING -> "LISTEN";
            case THINKING -> "THINK";
            case EXECUTING -> "RUN";
            case SUCCESS -> "OK";
            case ERROR -> "ERR";
            case SLEEPING -> "ZZZ";
        };
        String c = switch (mood) {
            case IDLE -> AnsiPalette.CYAN;
            case LISTENING -> AnsiPalette.MAGENTA;
            case THINKING -> AnsiPalette.VIOLET;
            case EXECUTING -> AnsiPalette.GOLD;
            case SUCCESS -> AnsiPalette.LIME;
            case ERROR -> AnsiPalette.CORAL;
            case SLEEPING -> AnsiPalette.SOFT;
        };
        return colors.bold(c, "◈ " + label + " ◈");
    }

    private String frame(String primary, String accent, String face, String status, String body, String tagline) {
        String border = colors.paint(primary, "╭──────────────╮");
        String borderB = colors.paint(primary, "╰──────────────╯");
        String faceLine = colors.paint(primary, "│")
                + colors.bold(accent, face)
                + colors.paint(primary, "│")
                + " "
                + colors.bold(primary, status.trim());
        String bodyLine = colors.paint(primary, "│")
                + colors.paint(AnsiPalette.WHITE, body)
                + colors.paint(primary, "│")
                + " "
                + colors.paint(AnsiPalette.SOFT, tagline.trim());
        String antenna = colors.paint(accent, "     ▲  ▲");
        String name = colors.bold(AnsiPalette.MAGENTA, "   Aether")
                + colors.paint(AnsiPalette.CYAN, " · desktop companion");
        return antenna + "\n" + border + "\n" + faceLine + "\n" + bodyLine + "\n" + borderB + "\n" + name + "\n";
    }

    public String renderIntro() {
        String title = colors.bold(AnsiPalette.MAGENTA, "╔══════════════════════════════════════╗\n")
                + colors.bold(AnsiPalette.CYAN, "║   ✦  AETHER  ·  AI Desktop Agent  ✦  ║\n")
                + colors.bold(AnsiPalette.MAGENTA, "╚══════════════════════════════════════╝");
        return title + "\n" + renderFrame();
    }
}

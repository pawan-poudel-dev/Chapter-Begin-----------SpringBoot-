package com.aidesktopagent.ui;

import com.aidesktopagent.actions.ActionResult;
import com.aidesktopagent.agent.Agent;
import com.aidesktopagent.agent.AgentException;
import com.aidesktopagent.avatar.AvatarMood;
import com.aidesktopagent.avatar.RobotAvatar;
import com.aidesktopagent.config.AppConfig;
import com.aidesktopagent.logging.LoggingService;
import com.aidesktopagent.voice.SpeechToText;
import com.aidesktopagent.voice.TextToSpeech;
import com.aidesktopagent.voice.VoiceManager;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Wires the always-on-top companion UI to Agent + optional VoiceManager.
 */
public final class CompanionController implements AutoCloseable {

    private final CompanionWindow window;
    private final Agent agent;
    private final RobotAvatar avatar;
    private final VoiceManager voice; // nullable if no STT
    private final TextToSpeech tts;
    private final LoggingService log;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "companion-worker");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final CountDownLatch closedLatch = new CountDownLatch(1);

    public CompanionController(AppConfig config,
                               Agent agent,
                               RobotAvatar avatar,
                               SpeechToText stt,
                               TextToSpeech tts,
                               Function<com.aidesktopagent.actions.Action, Boolean> confirm,
                               LoggingService log) {
        this.agent = agent;
        this.avatar = avatar;
        this.tts = tts;
        this.log = log != null ? log : LoggingService.forComponent("Companion");
        this.window = new CompanionWindow(avatar);
        this.voice = (stt != null)
                ? new VoiceManager(config, agent, stt, tts, confirm)
                : null;

        window.setOnTextCommand(this::handleText);
        window.setOnListenRequest(this::handleListen);
        window.setOnStopListen(() -> {
            if (voice != null) {
                voice.stop();
            }
            window.setListening(false);
            avatar.setMood(AvatarMood.IDLE);
        });
        window.setOnClose(this::close);

        window.appendLog("Aether Buddy: Hey! I'm happy you're here.");
        window.appendLog("Tap me & speak, or type below — I answer out loud.");
        window.appendLog("Try: how are you · play a song · flip a coin · surprise me");
        if (voice == null) {
            window.appendLog("Note: no cloud STT key — type commands, or set GEMINI/OPENAI key for Whisper STT.");
        }
    }

    public void show() {
        window.show();
        speakQuiet("Hey! Aether Buddy here. Tap me and talk — I will answer out loud.");
    }

    private void handleText(String text) {
        if (text.equalsIgnoreCase("quit") || text.equalsIgnoreCase("exit")) {
            close();
            return;
        }
        runCommand(text);
    }

    private void handleListen() {
        if (voice == null) {
            window.setStatus("Voice STT unavailable — type your command");
            window.appendLog("Install/configure STT (API key) for microphone mode.");
            return;
        }
        if (!busy.compareAndSet(false, true)) {
            window.setStatus("Busy — wait a moment");
            return;
        }
        window.setListening(true);
        worker.execute(() -> {
            try {
                avatar.setMood(AvatarMood.LISTENING);
                // VoiceManager.listenOnce processes STT → agent → TTS internally in some setups;
                // we call process via listenOnce path
                window.appendLog("Listening… speak your command.");
                voice.listenOnce();
                avatar.setMood(AvatarMood.SUCCESS);
                window.setStatus("Ready — tap again to speak");
                window.appendLog("Finished that request.");
            } catch (Exception e) {
                avatar.setMood(AvatarMood.ERROR);
                window.appendLog("Voice error: " + e.getMessage());
                window.setStatus("Voice failed — try typing");
                log.failure("companion-listen", e);
            } finally {
                window.setListening(false);
                busy.set(false);
                avatar.setMood(AvatarMood.IDLE);
            }
        });
    }

    private void runCommand(String text) {
        if (!busy.compareAndSet(false, true)) {
            window.appendLog("(busy, please wait)");
            return;
        }
        worker.execute(() -> {
            try {
                avatar.setMood(AvatarMood.THINKING);
                window.setStatus("Thinking…");
                List<ActionResult> results = agent.handle(text);
                avatar.setMood(AvatarMood.EXECUTING);
                StringBuilder sb = new StringBuilder();
                for (ActionResult r : results) {
                    String line = (r.isSuccess() ? "✓ " : "✗ ") + r.getMessage();
                    window.appendLog(line);
                    if (sb.length() > 0) {
                        sb.append(". ");
                    }
                    sb.append(r.getMessage());
                }
                boolean ok = results.stream().allMatch(ActionResult::isSuccess);
                avatar.setMood(ok ? AvatarMood.SUCCESS : AvatarMood.ERROR);
                window.setStatus(ok ? "Done" : "Something failed");
                String spoken = ok
                        ? "Okay! " + (sb.length() > 160 ? sb.substring(0, 157) + "..." : sb.toString())
                        : "Hmm, there was a problem. " + (sb.length() > 120 ? sb.substring(0, 117) + "..." : sb.toString());
                window.appendLog("Aether: " + spoken);
                speakQuiet(spoken);
            } catch (AgentException e) {
                avatar.setMood(AvatarMood.ERROR);
                window.appendLog("✗ " + e.getMessage());
                window.setStatus("Error");
                window.appendLog("Aether: Sorry, I could not do that.");
                speakQuiet("Sorry, I could not do that. Try saying it another way.");
            } catch (Exception e) {
                avatar.setMood(AvatarMood.ERROR);
                window.appendLog("✗ " + e.getMessage());
                log.failure("companion-cmd", e);
            } finally {
                busy.set(false);
                SwingUtilities.invokeLater(() -> avatar.setMood(AvatarMood.IDLE));
            }
        });
    }

    private void speakQuiet(String msg) {
        if (tts == null) {
            return;
        }
        try {
            tts.speak(msg);
        } catch (Exception ignored) {
        }
    }

    public void awaitClose() throws InterruptedException {
        closedLatch.await();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            if (voice != null) {
                voice.close();
            }
        } catch (Exception ignored) {
        }
        worker.shutdownNow();
        window.dispose();
        closedLatch.countDown();
    }
}


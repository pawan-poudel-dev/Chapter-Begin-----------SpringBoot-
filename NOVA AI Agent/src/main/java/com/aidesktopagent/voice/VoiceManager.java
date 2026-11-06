package com.aidesktopagent.voice;

import com.aidesktopagent.agent.Agent;
import com.aidesktopagent.agent.AgentState;
import com.aidesktopagent.agent.AgentStateManager;
import com.aidesktopagent.config.AppConfig;
import com.aidesktopagent.logging.LoggingService;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Owns microphone lifecycle, listening modes, and the voice command processor.
 *
 * <p>Modes:
 * <ul>
 *   <li><b>PUSH_TO_TALK</b> — {@link #listenOnce()} records a single utterance</li>
 *   <li><b>CONTINUOUS</b> — loop listen → process until {@link #stop()}</li>
 * </ul>
 *
 * <p>Optional wake-word: if configured, continuous mode ignores utterances that
 * do not start with the wake phrase (simple prefix match; not a neural detector).
 */
public final class VoiceManager implements AutoCloseable {

    public enum ListenMode {
        PUSH_TO_TALK,
        CONTINUOUS
    }

    private final AppConfig config;
    private final MicrophoneManager microphone;
    private final SpeechToText stt;
    private final TextToSpeech tts;
    private final AgentStateManager stateManager;
    private final VoiceCommandProcessor processor;
    private final LoggingService log;
    private final Locale language;
    private final String wakeWord; // nullable
    private final int maxUtteranceMs;
    private final int silenceMs;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "voice-worker");
        t.setDaemon(true);
        return t;
    });

    public VoiceManager(AppConfig config,
                        Agent agent,
                        SpeechToText stt,
                        TextToSpeech tts,
                        Function<com.aidesktopagent.actions.Action, Boolean> confirmationHandler) {
        this(config, agent, new MicrophoneManager(), stt, tts,
                new AgentStateManager(), confirmationHandler,
                LoggingService.forComponent("VoiceManager"),
                Locale.ENGLISH, null, 8_000, 1_200);
    }

    public VoiceManager(AppConfig config,
                        Agent agent,
                        MicrophoneManager microphone,
                        SpeechToText stt,
                        TextToSpeech tts,
                        AgentStateManager stateManager,
                        Function<com.aidesktopagent.actions.Action, Boolean> confirmationHandler,
                        LoggingService log,
                        Locale language,
                        String wakeWord,
                        int maxUtteranceMs,
                        int silenceMs) {
        this.config = Objects.requireNonNull(config);
        this.microphone = Objects.requireNonNull(microphone);
        this.stt = Objects.requireNonNull(stt);
        this.tts = Objects.requireNonNull(tts);
        this.stateManager = Objects.requireNonNull(stateManager);
        this.processor = new VoiceCommandProcessor(agent, stateManager, tts, confirmationHandler);
        this.log = log != null ? log : LoggingService.forComponent("VoiceManager");
        this.language = language != null ? language : Locale.ENGLISH;
        this.wakeWord = wakeWord != null && !wakeWord.isBlank() ? wakeWord.trim().toLowerCase(Locale.ROOT) : null;
        this.maxUtteranceMs = maxUtteranceMs > 0 ? maxUtteranceMs : 8_000;
        this.silenceMs = silenceMs > 0 ? silenceMs : 1_200;

        stateManager.addListener(s -> printMicStatus(s));
    }

    public AgentStateManager getStateManager() {
        return stateManager;
    }

    public VoiceCommandProcessor getProcessor() {
        return processor;
    }

    public boolean isMicrophoneActive() {
        return microphone.isActive();
    }

    /**
     * Capture one utterance, transcribe, process. Push-to-talk entry point.
     */
    public void listenOnce() {
        if (stateManager.isStopped()) {
            log.warn("Agent stopped — call clearCancel/reset before listening");
            return;
        }
        if (stateManager.isBusy() || stateManager.getState() == AgentState.LISTENING) {
            log.warn("Already busy or listening");
            return;
        }

        try {
            stateManager.transitionTo(AgentState.LISTENING);
            speakSafe("Go ahead, I'm listening.");
            printStatus("MIC ON — speak now");

            byte[] wav = microphone.captureUtterance(maxUtteranceMs, silenceMs);
            if (processor.isCancelRequested()) {
                return;
            }

            stateManager.transitionTo(AgentState.TRANSCRIBING);
            printStatus("MIC OFF — transcribing");
            String text = stt.transcribe(wav, language);
            log.info("Heard: " + text);

            if (wakeWord != null && !text.toLowerCase(Locale.ROOT).contains(wakeWord)) {
                log.info("Wake word missing — ignoring");
                speakSafe("Say " + wakeWord + " followed by your command.");
                stateManager.transitionTo(AgentState.IDLE);
                return;
            }

            String command = stripWakeWord(text);
            processor.processTranscription(command);
        } catch (SpeechRecognitionException e) {
            handleSttError(e);
        } catch (Exception e) {
            log.failure("listenOnce", e);
            stateManager.transitionTo(AgentState.ERROR);
            speakSafe("Listening failed.");
            stateManager.transitionTo(AgentState.IDLE);
        } finally {
            microphone.stopCapture();
            printStatus("MIC OFF");
        }
    }

    /**
     * Continuous listening loop on a background thread.
     */
    public void startContinuous() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        processor.clearCancel();
        worker.submit(() -> {
            log.info("Continuous listening started");
            speakSafe("Continuous listening on.");
            while (running.get() && !stateManager.isStopped()) {
                try {
                    if (stateManager.isBusy()) {
                        Thread.sleep(200);
                        continue;
                    }
                    listenOnce();
                    Thread.sleep(400);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.failure("continuous", e);
                }
            }
            running.set(false);
            log.info("Continuous listening ended");
        });
    }

    public void stop() {
        running.set(false);
        microphone.stopCapture();
        processor.requestCancel();
        printStatus("MIC OFF — stopped");
    }

    /** Process text as if it were transcribed (tests / hybrid CLI). */
    public void processTextAsVoice(String text) {
        processor.processTranscription(text);
    }

    @Override
    public void close() {
        stop();
        worker.shutdownNow();
        try {
            stt.close();
        } catch (Exception ignored) {
        }
        try {
            tts.close();
        } catch (Exception ignored) {
        }
        microphone.close();
    }

    private void handleSttError(SpeechRecognitionException e) {
        log.warn("STT error [" + e.getKind() + "]: " + e.getMessage());
        stateManager.transitionTo(AgentState.ERROR);
        String msg = switch (e.getKind()) {
            case MICROPHONE_UNAVAILABLE -> "No microphone is available.";
            case PERMISSION_DENIED -> "Microphone permission is denied.";
            case NETWORK -> "Speech service network error.";
            case TIMEOUT -> "Speech recognition timed out.";
            case UNCLEAR -> "I couldn't understand that.";
            case CANCELLED -> "Cancelled.";
            default -> "Speech recognition failed.";
        };
        speakSafe(msg);
        stateManager.transitionTo(AgentState.IDLE);
    }

    private String stripWakeWord(String text) {
        if (wakeWord == null) {
            return text;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf(wakeWord);
        if (idx >= 0) {
            return text.substring(idx + wakeWord.length()).replaceFirst("^[,:\\s]+", "").trim();
        }
        return text;
    }

    private void speakSafe(String t) {
        try {
            tts.speak(t);
        } catch (SpeechSynthesisException e) {
            System.out.println("[TTS] " + t);
        }
    }

    private void printMicStatus(AgentState s) {
        if (s == AgentState.LISTENING) {
            printStatus("MIC ON (" + s + ")");
        } else if (microphone.isActive()) {
            printStatus("MIC ON (" + s + ")");
        } else {
            printStatus("MIC OFF (" + s + ")");
        }
    }

    private static void printStatus(String msg) {
        System.out.println("[Voice] " + msg);
    }

    /**
     * Factory: Whisper if API key present, otherwise caller should inject Mock/Simulated.
     */
    public static SpeechToText createDefaultStt(AppConfig config) {
        if (config.hasApiKeyConfigured()) {
            return new WhisperHttpSpeechToText(config);
        }
        return new SpeechToText() {
            @Override
            public String transcribe(byte[] audio, Locale language) throws SpeechRecognitionException {
                throw new SpeechRecognitionException(
                        SpeechRecognitionException.Kind.PROVIDER,
                        "No speech provider configured. Set OPENAI_API_KEY for Whisper, "
                                + "or use MockSpeechToText / type commands in the CLI.");
            }

            @Override
            public String providerName() {
                return "unavailable";
            }
        };
    }

    public static TextToSpeech createDefaultTts() {
        java.util.List<TextToSpeech> chain = new java.util.ArrayList<>();
        if (WindowsTextToSpeech.isSupported()) {
            chain.add(new WindowsTextToSpeech());
        }
        SystemProcessTextToSpeech sys = new SystemProcessTextToSpeech();
        if (sys.isAvailable()) {
            chain.add(sys);
        }
        chain.add(new ConsoleTextToSpeech());
        return new CompositeTextToSpeech(chain);
    }
}


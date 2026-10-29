package com.aidesktopagent;

import com.aidesktopagent.actions.Action;
import com.aidesktopagent.actions.ActionParser;
import com.aidesktopagent.actions.ActionResult;
import com.aidesktopagent.actions.ActionValidator;
import com.aidesktopagent.agent.Agent;
import com.aidesktopagent.ai.AIClient;
import com.aidesktopagent.ai.OpenAiCompatibleClient;
import com.aidesktopagent.ai.RuleBasedAIClient;
import com.aidesktopagent.browser.DesktopBrowserController;
import com.aidesktopagent.coding.CodingAssistant;
import com.aidesktopagent.computer.ApplicationExecutor;
import com.aidesktopagent.computer.BrowserExecutor;
import com.aidesktopagent.computer.CodingActionExecutor;
import com.aidesktopagent.computer.CompositeActionExecutor;
import com.aidesktopagent.computer.FileExecutor;
import com.aidesktopagent.computer.KeyboardExecutor;
import com.aidesktopagent.computer.MediaActionExecutor;
import com.aidesktopagent.music.MediaController;

import java.nio.file.Path;
import com.aidesktopagent.config.AppConfig;
import com.aidesktopagent.logging.LoggingBootstrap;
import com.aidesktopagent.logging.LoggingService;
import com.aidesktopagent.voice.MockSpeechToText;
import com.aidesktopagent.voice.SpeechToText;
import com.aidesktopagent.voice.TextToSpeech;
import com.aidesktopagent.voice.VoiceManager;
import com.aidesktopagent.voice.WhisperHttpSpeechToText;
import com.aidesktopagent.voice.WindowsSpeechToText;
import com.aidesktopagent.ui.CompanionController;
import com.aidesktopagent.avatar.RobotAvatar;
import java.awt.GraphicsEnvironment;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/**
 * Entry point for the AI Desktop Agent.
 *
 * <p>Architecture:
 * <pre>
 *   Microphone → SpeechToText → Agent → ActionPlan → Validator → Executor → TTS
 *   or typed text → Agent → …
 * </pre>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        LoggingBootstrap.init();
        LoggingService logging = LoggingService.application();
        logging.startup("Initializing AI Desktop Agent...");

        try {
            AppConfig config = AppConfig.load();
            logging.info("Configuration loaded: " + config);

            AIClient aiClient = selectAiClient(config, logging);
            Agent agent = buildAgent(aiClient, logging);

            RobotAvatar avatar = new RobotAvatar();
            System.out.print(avatar.renderIntro());
            System.out.println("AI Desktop Agent started successfully.");
            logging.startup("AI Desktop Agent started successfully.");

            boolean companion = hasFlag(args, "--companion") || hasFlag(args, "--ui")
                    || hasFlag(args, "--jarvis");
            boolean voiceMode = hasFlag(args, "--voice") || config.isVoiceEnabled();
            boolean continuous = hasFlag(args, "--continuous");
            String[] commandArgs = stripFlags(args);

            // Default: open always-on-top companion when no command args and a display exists
            boolean hasDisplay = !GraphicsEnvironment.isHeadless();
            if (companion || (commandArgs.length == 0 && !voiceMode && hasDisplay
                    && !hasFlag(args, "--cli") && !hasFlag(args, "--repl"))) {
                runCompanion(config, agent, avatar, logging);
            } else if (voiceMode) {
                runVoiceMode(config, agent, logging, continuous, commandArgs);
            } else if (commandArgs.length > 0) {
                runCommand(agent, String.join(" ", commandArgs), logging);
            } else if (System.console() != null || !System.getenv().containsKey("CI")) {
                runInteractive(agent, config, logging);
            } else {
                logging.info("No args and non-interactive; exiting after startup.");
            }

            shutdown(logging);
        } catch (Exception e) {
            logging.failure("startup", e);
            System.err.println("AI Desktop Agent failed to start: " + e.getMessage());
            System.exit(1);
        }
    }


    /**
     * Always-on-top companion (Jarvis-style). Original robot Aether — not Doraemon.
     * Click robot / Space = voice; type + Enter = text command.
     */
    private static void runCompanion(AppConfig config, Agent agent, RobotAvatar avatar,
                                     LoggingService logging) {
        logging.info("Starting companion UI (always-on-top)");
        SpeechToText stt = null;
        try {
            stt = selectStt(config, logging);
        } catch (Exception e) {
            logging.warn("STT unavailable: " + e.getMessage());
        }
        TextToSpeech tts = VoiceManager.createDefaultTts();
        try (CompanionController companion = new CompanionController(
                config, agent, avatar, stt, tts, Main::confirmDestructive, logging)) {
            companion.show();
            System.out.println();
            System.out.println("Companion is open on top of your windows.");
            System.out.println("  • Click the robot or press Space = speak");
            System.out.println("  • Type in the box + Enter = text command");
            System.out.println("  • Close the window or type quit to exit");
            System.out.println("  (Camera hand-gestures not enabled — tap the robot to talk)");
            companion.awaitClose();
        } catch (Exception e) {
            logging.failure("companion", e);
            System.err.println("Companion UI failed: " + e.getMessage());
            System.err.println("Falling back to text mode. Use --cli for REPL.");
            runInteractive(agent, config, logging);
        }
    }

    private static void runVoiceMode(AppConfig config, Agent agent, LoggingService logging,
                                     boolean continuous, String[] commandArgs) {
        SpeechToText stt = selectStt(config, logging);
        TextToSpeech tts = VoiceManager.createDefaultTts();
        logging.info("STT provider=" + stt.providerName() + " TTS provider=" + tts.providerName());

        try (VoiceManager voice = new VoiceManager(config, agent, stt, tts, Main::confirmDestructive)) {
            System.out.println();
            System.out.println("Voice mode. Privacy: audio is only sent to the STT provider");
            System.out.println("when you finish an utterance (Whisper if OPENAI_API_KEY is set).");
            System.out.println("Commands: speak, or type text; 'stop agent' emergency stop; 'quit' exits.");
            System.out.println();

            if (commandArgs.length > 0) {
                // Simulate voice from CLI args (useful without a mic)
                voice.processTextAsVoice(String.join(" ", commandArgs));
                return;
            }

            if (continuous) {
                voice.startContinuous();
                System.out.println("Continuous listening — press Enter to stop.");
                try {
                    System.in.read();
                } catch (Exception ignored) {
                }
                voice.stop();
                return;
            }

            // Hybrid REPL: blank line = push-to-talk; typed line = text-as-voice
            Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
            while (true) {
                System.out.print("voice> (Enter=mic, text=type, quit=exit) ");
                if (!scanner.hasNextLine()) {
                    break;
                }
                String line = scanner.nextLine();
                if (line == null) {
                    break;
                }
                String trimmed = line.trim();
                if (trimmed.equalsIgnoreCase("quit") || trimmed.equalsIgnoreCase("exit")) {
                    voice.stop();
                    break;
                }
                if (trimmed.isEmpty()) {
                    voice.listenOnce();
                } else {
                    voice.processTextAsVoice(trimmed);
                }
            }
        } catch (Exception e) {
            logging.failure("voice-mode", e);
            System.err.println("Voice mode error: " + e.getMessage());
        }
    }

    private static SpeechToText selectStt(AppConfig config, LoggingService logging) {
        // Whisper needs a real OpenAI key (Gemini key is for chat planning, not Whisper)
        var openai = com.aidesktopagent.config.ConfigLoader.resolveSecret(
                com.aidesktopagent.config.ConfigLoader.ENV_OPENAI_API_KEY);
        if (openai.isPresent()) {
            logging.info("Using OpenAI Whisper for speech-to-text");
            return new WhisperHttpSpeechToText(config);
        }
        if (WindowsSpeechToText.isSupported()) {
            logging.info("Using Windows System.Speech for speech-to-text (free, on-device)");
            return new WindowsSpeechToText();
        }
        logging.warn("No Whisper key and not on Windows — voice needs typing or OPENAI_API_KEY");
        return new MockSpeechToText("Open Chrome");
    }

        private static AIClient selectAiClient(AppConfig config, LoggingService logging) {
        String userName = System.getenv().getOrDefault("AGENT_USER_NAME",
                System.getProperty("agent.user.name", "Pawan"));
        if (config.hasApiKeyConfigured()) {
            logging.info("Using OpenAI-compatible client (provider=" + config.getAiProvider() + ")");
            return new OpenAiCompatibleClient(config);
        }
        logging.info("No API key found — using rule-based offline AI client (user=" + userName + ")");
        return new RuleBasedAIClient(logging, userName);
    }

    private static Agent buildAgent(AIClient aiClient, LoggingService logging) {
        var browser = new DesktopBrowserController();
        var media = new MediaController(browser);
        Path codingRoot = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        CodingAssistant coding = null;
        try {
            coding = new CodingAssistant(codingRoot);
        } catch (Exception e) {
            logging.warn("Coding workspace unavailable: " + e.getMessage());
        }

        var executors = new java.util.ArrayList<com.aidesktopagent.computer.ActionExecutor>();
        executors.add(new ApplicationExecutor());
        executors.add(new BrowserExecutor(browser, logging));
        executors.add(new MediaActionExecutor(media));

        if (coding != null) {
            var workspace = coding.getWorkspace();
            var editor = new com.aidesktopagent.coding.SafeCodeEditor(workspace);
            var ideLauncher = new com.aidesktopagent.coding.IdeLauncher(
                    new com.aidesktopagent.coding.IdeDetector(), workspace);
            var buildRunner = new com.aidesktopagent.coding.BuildRunner(workspace);
            executors.add(new CodingActionExecutor(
                    coding, editor, ideLauncher, buildRunner, Main::confirmDestructive, logging));
            var fileSearch = new com.aidesktopagent.files.FileSearchService(workspace);
            executors.add(new com.aidesktopagent.computer.FileSearchExecutor(fileSearch));
        }

        Path prefFile = codingRoot.resolve("data").resolve("preferences.json");
        var prefs = new com.aidesktopagent.memory.PreferencesStore(prefFile);
        executors.add(new com.aidesktopagent.computer.PreferenceExecutor(prefs));
        var notes = new com.aidesktopagent.memory.NotesStore(codingRoot.resolve("data").resolve("notes.json"));
        executors.add(new com.aidesktopagent.computer.UtilityExecutor(notes, codingRoot));
        if (coding != null) {
            executors.add(new com.aidesktopagent.computer.GitStatusExecutor(coding.getWorkspace()));
        }
        try {
            var filePaths = new com.aidesktopagent.security.SafePathResolver(codingRoot);
            executors.add(new FileExecutor(filePaths));
        } catch (Exception e) {
            logging.warn("FileExecutor workspace bind failed: " + e.getMessage());
        }
        executors.add(new KeyboardExecutor());

        // Day 35: wrap each executor with permission checks
        // Interactive profile: write/coding/build enabled explicitly (shell still denied)
        var permissions = com.aidesktopagent.security.PermissionRegistry.interactiveDesktop();
        logging.info("Permissions: " + permissions.grantedScopes());
        var guarded = new java.util.ArrayList<com.aidesktopagent.computer.ActionExecutor>();
        for (var ex : executors) {
            guarded.add(new com.aidesktopagent.computer.PermissionGuardedExecutor(ex, permissions));
        }

        return new Agent(
                aiClient,
                new ActionParser(),
                new ActionValidator(),
                new CompositeActionExecutor(guarded),
                Main::confirmDestructive,
                logging
        );
    }

    private static boolean confirmDestructive(Action action) {
        System.out.println();
        System.out.println("⚠  Confirmation required");
        System.out.println("   Operation : " + action.getType());
        System.out.println("   Target    : " + action.getTarget().orElse("(none)"));
        action.getParameter("destination").or(() -> action.getParameter("to"))
                .ifPresent(d -> System.out.println("   Destination: " + d));
        System.out.println("   Scope     : " + (action.isDestructive() ? "DESTRUCTIVE / irreversible risk" : "elevated"));
        System.out.println("   Reversible: no (unless you have backups)");
        System.out.print("   Type YES to proceed, anything else to cancel: ");
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(System.in, StandardCharsets.UTF_8));
            String line = reader.readLine();
            return line != null && line.trim().equalsIgnoreCase("YES");
        } catch (Exception e) {
            return false;
        }
    }

    private static void runCommand(Agent agent, String command, LoggingService logging) {
        try {
            System.out.println("→ " + command);
            List<ActionResult> results = agent.handle(command);
            for (ActionResult r : results) {
                String icon = r.isSuccess() ? "✓" : "✗";
                System.out.println(icon + " [" + r.getActionType() + "] " + r.getMessage());
            }
        } catch (Exception e) {
            logging.failure("command", e);
            System.err.println("Failed: " + e.getMessage());
        }
    }

    private static void runInteractive(Agent agent, AppConfig config, LoggingService logging) {
        System.out.println();
        System.out.println("Type a command (or 'quit' to exit). Examples:");
        System.out.println("  Open Chrome");
        System.out.println("  Create a folder called AI Projects");
        System.out.println("  Open YouTube");
        System.out.println("  Use --companion for always-on-top Aether UI");
        System.out.println("  Use --voice for microphone console mode");
        System.out.println("  Use --cli for text-only REPL");
        System.out.println();

        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
        while (true) {
            System.out.print("agent> ");
            if (!scanner.hasNextLine()) {
                break;
            }
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")) {
                break;
            }
            if (line.equalsIgnoreCase("voice") || line.equalsIgnoreCase("listen")) {
                runVoiceMode(config, agent, logging, false, new String[0]);
                continue;
            }
            runCommand(agent, line, logging);
            System.out.println();
        }
    }

    private static boolean hasFlag(String[] args, String flag) {
        return Arrays.stream(args).anyMatch(a -> a.equalsIgnoreCase(flag));
    }

    private static String[] stripFlags(String[] args) {
        return Arrays.stream(args)
                .filter(a -> !a.startsWith("--"))
                .toArray(String[]::new);
    }

    private static void shutdown(LoggingService logging) {
        logging.shutdown("Shutting down AI Desktop Agent...");
        logging.shutdown("AI Desktop Agent shut down cleanly.");
    }
}

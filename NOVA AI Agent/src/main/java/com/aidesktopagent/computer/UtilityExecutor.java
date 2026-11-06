package com.aidesktopagent.computer;

import com.aidesktopagent.actions.Action;
import com.aidesktopagent.actions.ActionResult;
import com.aidesktopagent.actions.ActionType;
import com.aidesktopagent.logging.LoggingService;
import com.aidesktopagent.memory.NotesStore;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Clipboard, clock, calculator, notes, timers, help, system info.
 */
public final class UtilityExecutor implements ActionExecutor {

    private final NotesStore notes;
    private final SafeCalculator calc = new SafeCalculator();
    private final LoggingService log;
    private final Path workspaceRoot;
    private final ScheduledExecutorService timers =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "agent-timers");
                t.setDaemon(true);
                return t;
            });
    private final ConcurrentHashMap<String, String> activeTimers = new ConcurrentHashMap<>();

    public UtilityExecutor(NotesStore notes, Path workspaceRoot) {
        this(notes, workspaceRoot, LoggingService.forComponent("Utility"));
    }

    public UtilityExecutor(NotesStore notes, Path workspaceRoot, LoggingService log) {
        this.notes = Objects.requireNonNull(notes);
        this.workspaceRoot = workspaceRoot != null ? workspaceRoot : Path.of(".");
        this.log = log != null ? log : LoggingService.forComponent("Utility");
    }

    @Override
    public boolean supports(Action action) {
        return action.getType().isUtilityRelated();
    }

    @Override
    public ActionResult execute(Action action) {
        try {
            return switch (action.getType()) {
                case CLIPBOARD_READ -> readClipboard();
                case CLIPBOARD_WRITE -> writeClipboard(action.getTarget().orElse(""));
                case GET_TIME -> timeNow();
                case SYSTEM_INFO -> systemInfo();
                case CALCULATE -> calculate(action.getTarget().orElse(""));
                case SAVE_NOTE -> saveNote(action);
                case LIST_NOTES -> ActionResult.success(ActionType.LIST_NOTES, notes.listSummary());
                case READ_NOTE -> readNote(action.getTarget().orElse(""));
                case SET_TIMER -> setTimer(action);
                case SHOW_HELP -> ActionResult.success(ActionType.SHOW_HELP, helpText());
                case CHAT_REPLY -> chatReply(action);
                case WRITE_SNIPPET -> writeSnippet(action);
                default -> ActionResult.failure(action.getType(), "Unsupported utility action");
            };
        } catch (Exception e) {
            return ActionResult.failure(action.getType(), e.getMessage());
        }
    }

    private ActionResult readClipboard() {
        try {
            var cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (!cb.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                return ActionResult.failure(ActionType.CLIPBOARD_READ, "Clipboard has no text");
            }
            String text = (String) cb.getData(DataFlavor.stringFlavor);
            return ActionResult.success(ActionType.CLIPBOARD_READ,
                    text == null || text.isBlank() ? "(empty clipboard)" : text, text);
        } catch (Exception e) {
            return ActionResult.failure(ActionType.CLIPBOARD_READ,
                    "Clipboard unavailable (headless?): " + e.getMessage());
        }
    }

    private ActionResult writeClipboard(String text) {
        try {
            var cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            cb.setContents(new StringSelection(text), null);
            return ActionResult.success(ActionType.CLIPBOARD_WRITE,
                    "Copied to clipboard (" + text.length() + " chars)");
        } catch (Exception e) {
            return ActionResult.failure(ActionType.CLIPBOARD_WRITE,
                    "Clipboard write failed: " + e.getMessage());
        }
    }

    private ActionResult timeNow() {
        LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        String msg = now.format(DateTimeFormatter.ofPattern("EEEE, MMM d yyyy  HH:mm:ss", Locale.ENGLISH))
                + " (" + ZoneId.systemDefault() + ")";
        return ActionResult.success(ActionType.GET_TIME, msg);
    }

    private ActionResult systemInfo() {
        String msg = "OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + "\nArch: " + System.getProperty("os.arch")
                + "\nJava: " + System.getProperty("java.version")
                + "\nUser: " + System.getProperty("user.name")
                + "\nHome: " + System.getProperty("user.home")
                + "\nCWD: " + Path.of("").toAbsolutePath()
                + "\nProcessors: " + Runtime.getRuntime().availableProcessors()
                + "\nFree memory MB: " + (Runtime.getRuntime().freeMemory() / (1024 * 1024));
        return ActionResult.success(ActionType.SYSTEM_INFO, msg);
    }

    private ActionResult calculate(String expr) {
        double v = calc.evaluate(expr);
        String formatted = calc.format(v);
        return ActionResult.success(ActionType.CALCULATE, expr + " = " + formatted, formatted);
    }

    private ActionResult saveNote(Action action) {
        String name = action.getTarget().orElseThrow();
        String body = action.getParameter("body").orElse(action.getParameter("content").orElse(""));
        // "save note shopping milk" → name=shopping body=milk... if body empty use rest parsing upstream
        if (body.isBlank() && name.contains(" ")) {
            int sp = name.indexOf(' ');
            body = name.substring(sp + 1);
            name = name.substring(0, sp);
        }
        notes.save(name, body);
        return ActionResult.success(ActionType.SAVE_NOTE, "Saved note '" + name + "'");
    }

    private ActionResult readNote(String name) {
        return notes.read(name)
                .map(b -> ActionResult.success(ActionType.READ_NOTE, name + ": " + b, b))
                .orElse(ActionResult.failure(ActionType.READ_NOTE, "No note named '" + name + "'"));
    }

    private ActionResult setTimer(Action action) {
        String target = action.getTarget().orElse("60");
        int seconds;
        try {
            String n = target.toLowerCase(Locale.ROOT).replace("seconds", "")
                    .replace("second", "").replace("secs", "").replace("sec", "")
                    .replace("minutes", "m").replace("minute", "m").replace("mins", "m").replace("min", "m")
                    .trim();
            if (n.endsWith("m")) {
                seconds = (int) (Double.parseDouble(n.replace("m", "").trim()) * 60);
            } else {
                seconds = (int) Double.parseDouble(n.replaceAll("[^0-9.]", ""));
            }
        } catch (Exception e) {
            return ActionResult.failure(ActionType.SET_TIMER, "Could not parse duration: " + target);
        }
        if (seconds <= 0 || seconds > 24 * 3600) {
            return ActionResult.failure(ActionType.SET_TIMER, "Duration must be 1s–24h");
        }
        String label = action.getParameter("label").orElse("timer");
        String id = label + "-" + System.currentTimeMillis();
        activeTimers.put(id, label);
        timers.schedule(() -> {
            activeTimers.remove(id);
            log.info("TIMER FIRED: " + label + " (" + seconds + "s)");
            System.out.println("\n⏰ Timer done: " + label + " (" + seconds + "s)\n");
        }, seconds, TimeUnit.SECONDS);
        return ActionResult.success(ActionType.SET_TIMER,
                "Timer '" + label + "' set for " + Duration.ofSeconds(seconds).toSeconds() + "s");
    }

    private ActionResult chatReply(Action action) {
        String msg = action.getTarget().orElse("I'm here!");
        // Message is the spoken/chat response — no side effects
        return ActionResult.success(ActionType.CHAT_REPLY, msg, msg);
    }

    private ActionResult writeSnippet(Action action) throws Exception {
        String name = action.getTarget().orElse("snippet");
        String lang = action.getParameter("language").orElse("java");
        String code = action.getParameter("code").orElse(action.getParameter("content").orElse(""));
        String blurb = "a small program";
        if (code.isBlank()) {
            String kind = action.getParameter("kind").orElse(name).toLowerCase(Locale.ROOT);
            if (kind.contains("sum") || kind.contains("add two")) {
                name = "SumNumbers.java";
                blurb = "adds two numbers and prints the sum";
                code = """
                        public class SumNumbers {
                            public static void main(String[] args) {
                                int a = 10, b = 20;
                                System.out.println("Sum = " + (a + b));
                            }
                            public static int sum(int x, int y) { return x + y; }
                        }
                        """;
            } else if (kind.contains("hello")) {
                name = "HelloWorld.java";
                blurb = "prints Hello, World!";
                code = """
                        public class HelloWorld {
                            public static void main(String[] args) {
                                System.out.println("Hello, World!");
                            }
                        }
                        """;
            } else if (kind.contains("factorial")) {
                name = "Factorial.java";
                blurb = "computes factorial of 5";
                code = """
                        public class Factorial {
                            public static long factorial(int n) {
                                long r = 1;
                                for (int i = 2; i <= n; i++) r *= i;
                                return r;
                            }
                            public static void main(String[] args) {
                                System.out.println(factorial(5));
                            }
                        }
                        """;
            } else if (kind.contains("fib")) {
                name = "Fibonacci.java";
                blurb = "prints the first 10 Fibonacci numbers";
                code = """
                        public class Fibonacci {
                            public static void main(String[] args) {
                                int n = 10, a = 0, b = 1;
                                for (int i = 0; i < n; i++) {
                                    System.out.print(a + (i < n - 1 ? ", " : "\\n"));
                                    int c = a + b; a = b; b = c;
                                }
                            }
                        }
                        """;
            } else if (kind.contains("palindrome")) {
                name = "Palindrome.java";
                blurb = "checks if a string is a palindrome";
                code = """
                        public class Palindrome {
                            public static boolean isPalindrome(String s) {
                                int i = 0, j = s.length() - 1;
                                while (i < j) if (s.charAt(i++) != s.charAt(j--)) return false;
                                return true;
                            }
                            public static void main(String[] args) {
                                System.out.println(isPalindrome("level"));
                            }
                        }
                        """;
            } else {
                code = """
                        // Aether snippet
                        public class Snippet {
                            public static void main(String[] args) {
                                System.out.println("Ready");
                            }
                        }
                        """;
            }
        }
        if (!name.contains(".")) {
            name = name + (lang.equals("python") ? ".py" : ".java");
        }
        Path out = notesFileSibling("snippets").resolve(name.replaceAll("[^a-zA-Z0-9._-]", "_"));
        java.nio.file.Files.createDirectories(out.getParent());
        java.nio.file.Files.writeString(out, code);
        return ActionResult.success(ActionType.WRITE_SNIPPET,
                "Done! I saved " + out.getFileName() + " — " + blurb + ". Open it in your IDE when you're ready.",
                out.toString());
    }

    private Path notesFileSibling(String folder) {
        // workspaceRoot/data/snippets
        return workspaceRoot.resolve("data").resolve(folder);
    }

    private static String helpText() {
        return """
                Aether Buddy — try saying:
                • hi / how are you / bye / joke
                • open Chrome, Notepad, Calculator, Gmail, WhatsApp Web
                • open chrome and open youtube
                • play Millionaire by Honey Singh | youtube lofi beats
                • write a small code to sum numbers | analyze project
                • calculate 12*(3+4) | what time is it | system info
                • create folder AI Projects | save note ideas hello
                • set timer 5 minutes | git status | read clipboard
                Companion: --companion (tap robot & speak). Cloud: GEMINI_API_KEY.
                """.trim();
    }

    public void shutdown() {
        timers.shutdownNow();
    }
}

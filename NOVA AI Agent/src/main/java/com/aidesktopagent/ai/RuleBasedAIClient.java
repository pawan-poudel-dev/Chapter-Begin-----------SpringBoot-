package com.aidesktopagent.ai;

import com.aidesktopagent.logging.LoggingService;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline / fallback AI client that maps common English phrases to structured actions.
 */
public final class RuleBasedAIClient implements AIClient {

    private final LoggingService log;
    private final FastIntentRouter fastRouter = new FastIntentRouter();
    private final String userName;

    public RuleBasedAIClient() {
        this(LoggingService.forComponent("RuleBasedAIClient"), "friend");
    }

    public RuleBasedAIClient(LoggingService log) {
        this(log, "friend");
    }

    public RuleBasedAIClient(LoggingService log, String userName) {
        this.log = log != null ? log : LoggingService.forComponent("RuleBasedAIClient");
        this.userName = (userName == null || userName.isBlank()) ? "friend" : userName.trim();
    }

    @Override
    public String planActions(String userCommand) throws AIClientException {
        if (userCommand == null || userCommand.isBlank()) {
            throw new AIClientException("Empty command");
        }
        String cmd = userCommand.trim();
        String lower = cmd.toLowerCase(Locale.ROOT).replaceAll("[.!?]+$", "").trim();

        // Utilities
        if (matchesAny(lower, "what time is it", "what's the time", "current time", "time please")) {
            return json("GET_TIME", "");
        }
        if (matchesAny(lower, "system info", "sysinfo", "about system", "system information")) {
            return json("SYSTEM_INFO", "");
        }
        if (matchesAny(lower, "read clipboard", "paste clipboard", "show clipboard", "what's on the clipboard")) {
            return json("CLIPBOARD_READ", "");
        }
        Matcher cu = Pattern.compile("(?i)^(?:copy|clipboard write|put on clipboard)\s+(.+)$").matcher(cmd);
        if (cu.matches()) {
            return json("CLIPBOARD_WRITE", cu.group(1).trim());
        }
        Matcher calc = Pattern.compile("(?i)^(?:calculate|compute|math|what is|what's)\s+([0-9.+\\-*/()%\\s]+)$").matcher(cmd);
        if (calc.matches()) {
            return json("CALCULATE", calc.group(1).trim());
        }
        Matcher noteSave = Pattern.compile("(?i)^(?:save note|take note|note)\s+([\\w.-]+)\s+(.+)$").matcher(cmd);
        if (noteSave.matches()) {
            return jsonParams("SAVE_NOTE", noteSave.group(1).trim(), "body", noteSave.group(2).trim());
        }
        if (matchesAny(lower, "list notes", "show notes", "my notes")) {
            return json("LIST_NOTES", "");
        }
        Matcher noteRead = Pattern.compile("(?i)^(?:read note|show note)\s+([\\w.-]+)$").matcher(cmd);
        if (noteRead.matches()) {
            return json("READ_NOTE", noteRead.group(1).trim());
        }
        Matcher timer = Pattern.compile("(?i)^(?:set timer|timer|remind me in)\s+(.+)$").matcher(cmd);
        if (timer.matches()) {
            return json("SET_TIMER", timer.group(1).trim());
        }
        if (matchesAny(lower, "git status", "show git status", "repo status")) {
            return json("GIT_STATUS", "");
        }
        if (matchesAny(lower, "help", "what can you do", "commands", "show help")) {
            return json("SHOW_HELP", "");
        }


        // --- Greetings / small talk (chatbot style) ---
        if (matchesAny(lower, "hi", "hello", "hey", "hi aether", "hello aether", "hey aether",
                "hi ather", "hello ather", "hi aether buddy", "yo", "hola")) {
            return json("CHAT_REPLY", "Hi " + userName + "! I'm Aether Buddy. Say open Chrome, play a song, "
                    + "or ask me anything I can do on your desktop. What should we do?");
        }
        if (matchesAny(lower, "good morning")) {
            return json("CHAT_REPLY", "Good morning, " + userName + "! Ready when you are.");
        }
        if (matchesAny(lower, "good evening", "good afternoon")) {
            return json("CHAT_REPLY", "Good evening, " + userName + "! How can I help?");
        }
        if (matchesAny(lower, "good night", "goodnight", "bye", "goodbye", "see you", "see ya")) {
            return json("CHAT_REPLY", "Goodbye, " + userName + "! Call me anytime.");
        }
        if (matchesAny(lower, "how are you", "how's it going", "how are you doing", "how r you", "how are u", "how do you do")) {
            return json("CHAT_REPLY", "I'm fine, thank you " + userName + "! What about you? How can I help you today?");
        }
        if (matchesAny(lower, "i am fine", "i'm fine", "im fine", "i am good", "i'm good", "doing well")) {
            return json("CHAT_REPLY", "Glad to hear that, " + userName + "! What would you like me to do?");
        }
        if (matchesAny(lower, "thank you", "thanks", "thank you aether", "thanks aether", "thx")) {
            return json("CHAT_REPLY", "You're welcome, " + userName + "!");
        }
        if (matchesAny(lower, "who are you", "what are you", "your name")) {
            return json("CHAT_REPLY", "I'm Aether Buddy, your desktop AI assistant. I listen, talk back, open apps, "
                    + "play songs, manage files, and help with small code.");
        }
        if (matchesAny(lower, "what can you do", "help me", "capabilities", "features")) {
            return json("SHOW_HELP", "");
        }
        if (matchesAny(lower, "tell me a joke", "joke", "make me laugh")) {
            return json("CHAT_REPLY", "Why do Java developers wear glasses? Because they don't C#!");
        }
        if (matchesAny(lower, "are you there", "you there", "anyone home")) {
            return json("CHAT_REPLY", "Always here, " + userName + ". Listening.");
        }

        // --- Delightful extras ---
        if (matchesAny(lower, "flip a coin", "coin flip", "toss a coin", "heads or tails")) {
            boolean heads = (System.nanoTime() & 1) == 0;
            return json("CHAT_REPLY", heads
                    ? "It's heads! Lucky day, " + userName + "?"
                    : "It's tails! Want to go again?");
        }
        if (matchesAny(lower, "roll a dice", "roll a die", "roll dice", "dice roll")) {
            int n = 1 + (int) (Math.abs(System.nanoTime()) % 6);
            return json("CHAT_REPLY", "You rolled a " + n + "! " + (n == 6 ? "Nice!" : "Want another roll?"));
        }
        if (matchesAny(lower, "motivate me", "motivate", "i need motivation", "encourage me")) {
            String[] lines = {
                "You've got this, " + userName + "! One small step is still progress.",
                "Be proud of showing up today, " + userName + ". I'm here to help.",
                "Focus on the next five minutes — I'll handle the busywork with you."
            };
            return json("CHAT_REPLY", lines[(int) (Math.abs(System.nanoTime()) % lines.length)]);
        }
        if (matchesAny(lower, "fun fact", "tell me a fact", "random fact")) {
            String[] facts = {
                "Honey never spoils — jars found in ancient tombs were still edible!",
                "Octopuses have three hearts. Two pump blood to the gills.",
                "A day on Venus is longer than a year on Venus.",
                "The first computer bug was a real moth stuck in a relay in 1947."
            };
            return json("CHAT_REPLY", facts[(int) (Math.abs(System.nanoTime()) % facts.length)]);
        }
        if (matchesAny(lower, "surprise me", "something cool", "impress me")) {
            return json("CHAT_REPLY", "Try: play lofi beats, open chrome and open youtube, write factorial code, "
                    + "or set timer 25 minutes for a focus sprint. Your move, " + userName + "!");
        }
        if (matchesAny(lower, "i love you", "love you aether", "i like you")) {
            return json("CHAT_REPLY", "Aww — that means a lot, " + userName + "! I'm happiest when I make your day easier.");
        }
        if (matchesAny(lower, "sing a song", "sing for me")) {
            return json("CHAT_REPLY", "La la la~ I'm better at finding songs than singing them. Say: play your favorite track!");
        }
        if (matchesAny(lower, "good job", "well done", "you did well")) {
            return json("CHAT_REPLY", "Thank you, " + userName + "! Teamwork makes the dream work.");
        }
        // Focus / pomodoro
        if (matchesAny(lower, "pomodoro", "focus timer", "focus session", "start focus")) {
            return json("SET_TIMER", "25 minutes");
        }
        if (matchesAny(lower, "short break", "break timer", "tea break")) {
            return json("SET_TIMER", "5 minutes");
        }
        // More code templates by phrase
        if (lower.contains("factorial") && (lower.contains("code") || lower.contains("program") || lower.contains("write"))) {
            return jsonParams("WRITE_SNIPPET", "Factorial.java", "kind", "factorial");
        }
        if ((lower.contains("fibonacci") || lower.contains("fib ")) && (lower.contains("code") || lower.contains("program") || lower.contains("write"))) {
            return jsonParams("WRITE_SNIPPET", "Fibonacci.java", "kind", "fibonacci");
        }
        if (lower.contains("hello world") && (lower.contains("code") || lower.contains("program") || lower.contains("write") || lower.contains("create"))) {
            return jsonParams("WRITE_SNIPPET", "HelloWorld.java", "kind", "hello");
        }
        if (lower.contains("palindrome") && (lower.contains("code") || lower.contains("program") || lower.contains("write"))) {
            return jsonParams("WRITE_SNIPPET", "Palindrome.java", "kind", "palindrome");
        }


        Matcher nameM = Pattern.compile("(?i)^(?:my name is|i am|i'm|call me)\\s+([A-Za-z][A-Za-z0-9_ -]{0,30})$").matcher(cmd);
        if (nameM.matches()) {
            String n = nameM.group(1).trim();
            return jsonParams("SET_PREFERENCE", "user_name", "value", n);
        }

        // --- Multi-step opens ---
        if (lower.matches(".*\\bopen\\b.*\\bchrome\\b.*\\b(and|&)?\\b.*\\b(youtube|yt)\\b.*")
                || lower.matches(".*\\bopen\\b.*\\b(youtube|yt)\\b.*\\b(and|&)?\\b.*\\bchrome\\b.*")) {
            return multi(
                jsonAction("OPEN_APPLICATION", "Chrome"),
                jsonAction("OPEN_WEBSITE", "https://www.youtube.com")
            );
        }
        if (lower.matches(".*\\bopen\\b.*\\bchrome\\b.*\\b(and|&)?\\b.*\\bnotepad\\b.*")) {
            return multi(
                jsonAction("OPEN_APPLICATION", "Chrome"),
                jsonAction("OPEN_APPLICATION", "notepad")
            );
        }
        if (lower.contains("open gmail") && lower.contains("youtube")) {
            return multi(
                jsonAction("OPEN_WEBSITE", "https://mail.google.com"),
                jsonAction("OPEN_WEBSITE", "https://www.youtube.com")
            );
        }

        Matcher ytSearch2 = Pattern.compile("(?i)^(?:youtube|search youtube|find on youtube)\\s+(.+)$").matcher(cmd);
        if (ytSearch2.matches()) {
            return json("SEARCH_YOUTUBE", ytSearch2.group(1).trim());
        }

        // --- Play song by name / artist ---

        // --- Play song by name / artist ---
        Matcher song = Pattern.compile("(?i)^(?:play|run|put on)\\s+(?:the\\s+)?(?:song\\s+)?(.+?)(?:\\s+on\\s+youtube)?$").matcher(cmd);
        if (song.matches() && !lower.startsWith("play relaxing") && !lower.contains("spotify")
                && !lower.equals("play again")) {
            String q = song.group(1).trim();
            if (!q.isEmpty() && q.length() < 120
                    && !q.equalsIgnoreCase("youtube")
                    && !matchesAny(q.toLowerCase(Locale.ROOT), "pause", "next", "previous")) {
                return json("PLAY_YOUTUBE", q);
            }
        }
        // "millionaire by honey singh" style without play prefix if starts with run song
        Matcher runSong = Pattern.compile("(?i)^(?:run|play)\\s+song\\s+(.+)$").matcher(cmd);
        if (runSong.matches()) {
            return json("PLAY_YOUTUBE", runSong.group(1).trim());
        }

        // --- Write small code (sum etc.) ---
        if (lower.contains("sum") && (lower.contains("code") || lower.contains("program")
                || lower.contains("write") || lower.contains("create"))) {
            return jsonParams("WRITE_SNIPPET", "SumNumbers.java", "kind", "sum");
        }
        if (Pattern.compile("(?i)^(?:write|create|generate)\\s+(?:a\\s+)?(?:small\\s+)?(?:code|program|snippet)\\s+(.+)$").matcher(cmd).matches()) {
            Matcher wm = Pattern.compile("(?i)^(?:write|create|generate)\\s+(?:a\\s+)?(?:small\\s+)?(?:code|program|snippet)\\s+(.+)$").matcher(cmd);
            wm.matches();
            return jsonParams("WRITE_SNIPPET", "snippet.java", "kind", wm.group(1).trim());
        }


                // Instant path: O(1) exact intents (no regex)
        var fast = fastRouter.match(cmd);
        if (fast.isPresent()) {
            return fastRouter.toJson(fast.get());
        }

        // Preferences
        Matcher pm = Pattern.compile("(?i)^(?:set preference|prefer)\\s+(\\w+)\\s+(?:to\\s+)?(.+)$").matcher(cmd);
        if (pm.matches()) {
            return jsonParams("SET_PREFERENCE", pm.group(1).trim(), "value", pm.group(2).trim());
        }
        if (matchesAny(lower, "show preferences", "get preferences", "list preferences")) {
            return json("GET_PREFERENCE", "");
        }

        // Build / IDE
        if (matchesAny(lower, "run tests", "run the tests", "build project", "compile project", "mvn test")) {
            String goal = lower.contains("compile") ? "compile" : (lower.contains("package") ? "package" : "test");
            return json("RUN_BUILD", goal);
        }
        if (matchesAny(lower, "open in ide", "open project in ide", "open in intellij", "open in vscode", "open in code")) {
            return json("OPEN_IN_IDE", "");
        }

        // File search
        Matcher fs = Pattern.compile("(?i)^(?:find|search for)\\s+files?(?:\\s+named)?\\s+(.+)$").matcher(cmd);
        if (fs.matches()) {
            return json("SEARCH_FILES", fs.group(1).trim());
        }
        if (matchesAny(lower, "summarize folder", "folder summary")) {
            return json("SUMMARIZE_FOLDER", "");
        }

        // Coding assistant
        if (matchesAny(lower, "analyze project", "project summary", "describe project", "what project is this")) {
            return json("ANALYZE_PROJECT", "");
        }
        if (lower.startsWith("list source") || lower.startsWith("list sources")
                || lower.equals("list java files") || lower.equals("list classes")) {
            return json("LIST_SOURCE_FILES", "");
        }
        Matcher cm = Pattern.compile("(?i)^list\\s+sources?\\s+(?:named|called|matching)\\s+(.+)$").matcher(cmd);
        if (cm.matches()) {
            return json("LIST_SOURCE_FILES", cm.group(1).trim());
        }
        cm = Pattern.compile("(?i)^(?:read|show)\\s+(?:source\\s+|file\\s+)?(.+\\.java)$").matcher(cmd);
        if (cm.matches()) {
            return json("READ_SOURCE_FILE", cm.group(1).trim());
        }
        if (lower.contains("nullpointerexception") || lower.contains("caused by:")
                || lower.contains("exception in thread") || lower.startsWith("java.")) {
            return json("EXPLAIN_ERROR", cmd);
        }
        cm = Pattern.compile("(?i)^explain\\s+(?:this\\s+)?(?:error|exception|stack\\s*trace)\\s*:?\\s*(.+)$").matcher(cmd);
        if (cm.matches()) {
            return json("EXPLAIN_ERROR", cm.group(1).trim());
        }
        cm = Pattern.compile("(?i)^propose\\s+(?:a\\s+)?(?:change|fix|patch)\\s+(?:for\\s+|to\\s+)?(.+)$").matcher(cmd);
        if (cm.matches()) {
            return json("PROPOSE_CODE_CHANGE", cm.group(1).trim());
        }

        // Media transport (pause / resume / next / previous / status)
        if (matchesAny(lower, "pause", "pause that", "pause the video", "pause the music", "pause playback")) {
            return json("PAUSE_MEDIA", "");
        }
        if (matchesAny(lower, "resume", "resume playback", "continue playing", "continue", "unpause", "play again")) {
            return json("RESUME_MEDIA", "");
        }
        if (matchesAny(lower, "next", "next track", "next song", "skip", "skip track", "open the next video", "next video")) {
            return json("NEXT_TRACK", "");
        }
        if (matchesAny(lower, "previous", "previous track", "last track", "previous song")) {
            return json("PREVIOUS_TRACK", "");
        }
        if (matchesAny(lower, "media status", "what's playing", "what is playing", "now playing")) {
            return json("GET_MEDIA_STATUS", "");
        }

        // Spotify
        if (lower.equals("open spotify") || lower.equals("launch spotify")) {
            return json("OPEN_SPOTIFY", "");
        }
        Matcher m = Pattern.compile("(?i)^(?:play|put on)\\s+(.+?)\\s+on\\s+spotify[.!]?$").matcher(cmd);
        if (m.matches()) {
            return json("PLAY_SPOTIFY", m.group(1).trim());
        }
        m = Pattern.compile("(?i)^play\\s+(?:my\\s+)?(.+?)\\s+playlist(?:\\s+on\\s+spotify)?[.!]?$").matcher(cmd);
        if (m.matches()) {
            return json("PLAY_SPOTIFY", m.group(1).trim() + " playlist");
        }
        m = Pattern.compile("(?i)^(?:search(?:\\s+for)?|find)\\s+(.+?)\\s+on\\s+spotify[.!]?$").matcher(cmd);
        if (m.matches()) {
            return json("SEARCH_SPOTIFY", m.group(1).trim());
        }
        if (lower.startsWith("play ") && lower.contains("spotify")) {
            String q = cmd.replaceAll("(?i)play\\s+", "").replaceAll("(?i)\\s+on\\s+spotify", "").trim();
            return json("PLAY_SPOTIFY", q);
        }

        // YouTube
        if (lower.equals("open youtube") || lower.equals("launch youtube")) {
            return json("OPEN_WEBSITE", "https://www.youtube.com");
        }
        m = Pattern.compile("(?i)^(?:play|watch)\\s+(.+?)\\s+on\\s+youtube[.!]?$").matcher(cmd);
        if (m.matches()) {
            return json("PLAY_YOUTUBE", m.group(1).trim());
        }
        m = Pattern.compile("(?i)^(?:play|watch)\\s+(?:on\\s+)?youtube\\s+(.+?)[.!]?$").matcher(cmd);
        if (m.matches()) {
            return json("PLAY_YOUTUBE", m.group(1).trim());
        }
        m = Pattern.compile("(?i)^(?:find|search(?:\\s+for)?)\\s+(?:a\\s+)?(?:video\\s+(?:about\\s+|on\\s+)?)?(.+?)(?:\\s+on\\s+youtube)?[.!]?$").matcher(cmd);
        if (m.matches() && (lower.contains("youtube") || lower.contains("video") || lower.contains("tutorial"))) {
            return json("SEARCH_YOUTUBE", m.group(1).trim());
        }
        m = Pattern.compile("(?i)^find\\s+(?:me\\s+)?(?:a\\s+)?(.+?)[.!]?$").matcher(cmd);
        if (m.matches() && (lower.contains("tutorial") || lower.contains("video"))) {
            return json("SEARCH_YOUTUBE", m.group(1).trim());
        }
        // "Play a Java tutorial" / "Play relaxing music" without platform → YouTube search/play
        m = Pattern.compile("(?i)^play\\s+(?:a\\s+|some\\s+)?(.+?)[.!]?$").matcher(cmd);
        if (m.matches()) {
            String topic = m.group(1).trim();
            if (!topic.equalsIgnoreCase("spotify") && !topic.equalsIgnoreCase("youtube")) {
                return json("PLAY_YOUTUBE", topic);
            }
        }
        m = Pattern.compile("(?i)^put\\s+on\\s+(?:some\\s+)?(.+?)[.!]?$").matcher(cmd);
        if (m.matches()) {
            return json("PLAY_YOUTUBE", m.group(1).trim());
        }

        // OPEN APPLICATION / website
        m = Pattern.compile("(?i)^(?:open|launch|start)\\s+(.+?)(?:\\s+please)?[.!]?$").matcher(cmd);
        if (m.matches()) {
            String target = m.group(1).trim();
            if (target.equalsIgnoreCase("spotify")) {
                return json("OPEN_SPOTIFY", "");
            }
            if (isWebsiteish(target)) {
                return json("OPEN_WEBSITE", normalizeUrl(target));
            }
            if (isFolderish(target)) {
                return json("OPEN_FOLDER", stripFolderWords(target));
            }
            // "Open Chrome and search for X"
            Matcher combo = Pattern.compile("(?i)^(.+?)\\s+and\\s+search\\s+(?:for\\s+)?(.+)$").matcher(target);
            if (combo.matches()) {
                return json("SEARCH_WEB", combo.group(2).trim());
            }
            return json("OPEN_APPLICATION", cleanAppName(target));
        }

        m = Pattern.compile("(?i)^(?:close|quit|exit)\\s+(.+?)[.!]?$").matcher(cmd);
        if (m.matches()) {
            return json("CLOSE_APPLICATION", cleanAppName(m.group(1).trim()));
        }

        m = Pattern.compile("(?i)^(?:create|make)\\s+(?:a\\s+)?folder\\s+(?:called\\s+|named\\s+)?[\"']?(.+?)[\"']?[.!]?$").matcher(cmd);
        if (m.matches()) {
            return json("CREATE_FOLDER", m.group(1).trim());
        }

        m = Pattern.compile("(?i)^(?:create|make)\\s+(?:a\\s+)?file\\s+(?:called\\s+|named\\s+)?[\"']?(.+?)[\"']?[.!]?$").matcher(cmd);
        if (m.matches()) {
            return json("CREATE_FILE", m.group(1).trim());
        }

        m = Pattern.compile("(?i)^(?:delete|remove)\\s+(?:the\\s+)?(?:file\\s+)?[\"']?(.+?)[\"']?[.!]?$").matcher(cmd);
        if (m.matches()) {
            return json("DELETE_FILE", m.group(1).trim());
        }

        m = Pattern.compile("(?i)^(?:type|write)\\s+[\"']?(.+?)[\"']?[.!]?$").matcher(cmd);
        if (m.matches()) {
            return json("TYPE_TEXT", m.group(1).trim());
        }

        m = Pattern.compile("(?i)^(?:press|hit)\\s+(?:the\\s+)?(?:key\\s+)?[\"']?(.+?)[\"']?[.!]?$").matcher(cmd);
        if (m.matches()) {
            return json("PRESS_KEY", m.group(1).trim().toUpperCase(Locale.ROOT));
        }

        m = Pattern.compile("(?i)^(?:search(?:\\s+for)?|google)\\s+(.+?)[.!]?$").matcher(cmd);
        if (m.matches()) {
            return json("SEARCH_WEB", m.group(1).trim());
        }

        m = Pattern.compile("(?i)^(?:go\\s+to|browse|visit)\\s+(.+?)[.!]?$").matcher(cmd);
        if (m.matches()) {
            return json("OPEN_WEBSITE", normalizeUrl(m.group(1).trim()));
        }

        // Questions / free chat → reply; short app-like phrases → open app
        if (lower.endsWith("?") || lower.startsWith("what ") || lower.startsWith("why ")
                || lower.startsWith("how ") || lower.startsWith("who ") || lower.startsWith("when ")
                || lower.startsWith("where ") || lower.startsWith("can you ") || lower.startsWith("could you ")
                || lower.startsWith("tell me ") || lower.startsWith("explain ")) {
            log.info("Chat-style fallback for: " + cmd);
            return json("CHAT_REPLY", "I'm not sure about that offline, " + userName
                    + ". Try a clear command like open Chrome, play a song, calculate 2+2, or set GEMINI_API_KEY for smarter chat.");
        }
        log.warn("No specific rule matched; treating as OPEN_APPLICATION: " + cmd);
        return json("OPEN_APPLICATION", cleanAppName(cmd));
    }

    private static boolean matchesAny(String lower, String... phrases) {
        for (String p : phrases) {
            if (lower.equals(p)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWebsiteish(String t) {
        String l = t.toLowerCase(Locale.ROOT);
        return l.contains(".") || l.contains("http") || l.equals("youtube")
                || l.equals("google") || l.equals("github") || l.endsWith(".com")
                || l.endsWith(".org") || l.endsWith(".net");
    }

    private static boolean isFolderish(String t) {
        String l = t.toLowerCase(Locale.ROOT);
        return l.startsWith("folder ") || l.endsWith(" folder") || l.contains("directory");
    }

    private static String stripFolderWords(String t) {
        return t.replaceAll("(?i)\\b(folder|directory)\\b", "").trim();
    }

    private static String cleanAppName(String t) {
        return t.replaceAll("(?i)\\b(app|application|the)\\b", "").trim();
    }

    private static String normalizeUrl(String t) {
        String s = t.trim();
        if (s.equalsIgnoreCase("youtube")) return "https://www.youtube.com";
        if (s.equalsIgnoreCase("google")) return "https://www.google.com";
        if (s.equalsIgnoreCase("github")) return "https://github.com";
        if (s.equalsIgnoreCase("spotify")) return "https://open.spotify.com";
        if (!s.contains("://")) {
            if (!s.contains(".")) {
                s = s + ".com";
            }
            s = "https://" + s;
        }
        return s;
    }

    private static String jsonAction(String type, String target) {
        String et = target == null ? "" : target.replace("\\", "\\\\").replace("\"", "\\\"");
        if (et.isBlank()) {
            return "{ \"type\": \"%s\" }".formatted(type);
        }
        return "{ \"type\": \"%s\", \"target\": \"%s\" }".formatted(type, et);
    }

    private static String multi(String... actionJsonObjects) {
        return "{ \"actions\": [ " + String.join(", ", actionJsonObjects) + " ] }";
    }

    private static String jsonParams(String type, String target, String key, String value) {
        String et = target == null ? "" : target.replace("\\", "\\\\").replace("\"", "\\\"");
        String ev = value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
        return """
                {
                  "actions": [
                    {
                      "type": "%s",
                      "target": "%s",
                      "parameters": { "%s": "%s" }
                    }
                  ]
                }
                """.formatted(type, et, key, ev);
    }

    private static String json(String type, String target) {
        String escaped = target
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
        if (target == null || target.isBlank()) {
            return """
                    {
                      "actions": [
                        { "type": "%s" }
                      ]
                    }
                    """.formatted(type);
        }
        return """
                {
                  "actions": [
                    {
                      "type": "%s",
                      "target": "%s"
                    }
                  ]
                }
                """.formatted(type, escaped);
    }
}

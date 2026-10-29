package com.aidesktopagent.ai;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * O(1) exact-phrase router for common commands — keeps the agent snappy.
 */
public final class FastIntentRouter {

    public record Intent(String actionType, String target) {}

    private final Map<String, Intent> exact = new ConcurrentHashMap<>();

    public FastIntentRouter() {
        put("pause", "PAUSE_MEDIA", "");
        put("pause music", "PAUSE_MEDIA", "");
        put("pause video", "PAUSE_MEDIA", "");
        put("resume", "RESUME_MEDIA", "");
        put("continue", "RESUME_MEDIA", "");
        put("play again", "RESUME_MEDIA", "");
        put("next", "NEXT_TRACK", "");
        put("next track", "NEXT_TRACK", "");
        put("skip", "NEXT_TRACK", "");
        put("previous", "PREVIOUS_TRACK", "");
        put("previous track", "PREVIOUS_TRACK", "");
        put("status", "GET_MEDIA_STATUS", "");
        put("what's playing", "GET_MEDIA_STATUS", "");
        put("what is playing", "GET_MEDIA_STATUS", "");
        put("stop media", "STOP_MEDIA", "");
        put("stop music", "STOP_MEDIA", "");

        put("open youtube", "OPEN_WEBSITE", "https://www.youtube.com");
        put("open yt", "OPEN_WEBSITE", "https://www.youtube.com");
        put("open google", "OPEN_WEBSITE", "https://www.google.com");
        put("open github", "OPEN_WEBSITE", "https://github.com");
        put("open gmail", "OPEN_WEBSITE", "https://mail.google.com");
        put("open maps", "OPEN_WEBSITE", "https://maps.google.com");
        put("open whatsapp", "OPEN_WEBSITE", "https://web.whatsapp.com");
        put("open whatsapp web", "OPEN_WEBSITE", "https://web.whatsapp.com");
        put("open twitter", "OPEN_WEBSITE", "https://x.com");
        put("open x", "OPEN_WEBSITE", "https://x.com");
        put("open linkedin", "OPEN_WEBSITE", "https://www.linkedin.com");
        put("open spotify", "OPEN_SPOTIFY", "");
        put("open netflix", "OPEN_WEBSITE", "https://www.netflix.com");

        put("open chrome", "OPEN_APPLICATION", "Chrome");
        put("open google chrome", "OPEN_APPLICATION", "Chrome");
        put("open firefox", "OPEN_APPLICATION", "Firefox");
        put("open edge", "OPEN_APPLICATION", "msedge");
        put("open microsoft edge", "OPEN_APPLICATION", "msedge");
        put("open terminal", "OPEN_APPLICATION", "terminal");
        put("open cmd", "OPEN_APPLICATION", "cmd");
        put("open powershell", "OPEN_APPLICATION", "powershell");
        put("open code", "OPEN_APPLICATION", "code");
        put("open vscode", "OPEN_APPLICATION", "code");
        put("open visual studio code", "OPEN_APPLICATION", "code");
        put("open intellij", "OPEN_APPLICATION", "intellij");
        put("open idea", "OPEN_APPLICATION", "intellij");
        put("open notepad", "OPEN_APPLICATION", "notepad");
        put("open calculator", "OPEN_APPLICATION", "calc");
        put("open calc", "OPEN_APPLICATION", "calc");
        put("open explorer", "OPEN_APPLICATION", "explorer");
        put("open file explorer", "OPEN_APPLICATION", "explorer");
        put("open files", "OPEN_APPLICATION", "explorer");
        put("open paint", "OPEN_APPLICATION", "mspaint");
        put("open word", "OPEN_APPLICATION", "winword");
        put("open excel", "OPEN_APPLICATION", "excel");

        put("analyze project", "ANALYZE_PROJECT", "");
        put("project summary", "ANALYZE_PROJECT", "");
        put("list sources", "LIST_SOURCE_FILES", "");
        put("list classes", "LIST_SOURCE_FILES", "");
        put("run tests", "RUN_BUILD", "test");
        put("build project", "RUN_BUILD", "package");
        put("compile project", "RUN_BUILD", "compile");
        put("open in ide", "OPEN_IN_IDE", "");
        put("folder summary", "SUMMARIZE_FOLDER", "");
        put("summarize folder", "SUMMARIZE_FOLDER", "");
        put("git status", "GIT_STATUS", "");
        put("show preferences", "GET_PREFERENCE", "");

        put("help", "SHOW_HELP", "");
        put("what can you do", "SHOW_HELP", "");
        put("what time is it", "GET_TIME", "");
        put("time", "GET_TIME", "");
        put("date", "GET_TIME", "");
        put("system info", "SYSTEM_INFO", "");
        put("sysinfo", "SYSTEM_INFO", "");
        put("read clipboard", "CLIPBOARD_READ", "");
        put("clipboard", "CLIPBOARD_READ", "");
        put("list notes", "LIST_NOTES", "");
        put("show notes", "LIST_NOTES", "");
        put("pomodoro", "SET_TIMER", "25 minutes");
        put("focus timer", "SET_TIMER", "25 minutes");
        put("short break", "SET_TIMER", "5 minutes");
    }

    private void put(String phrase, String type, String target) {
        exact.put(phrase.toLowerCase(Locale.ROOT), new Intent(type, target));
    }

    public Optional<Intent> match(String command) {
        if (command == null || command.isBlank()) {
            return Optional.empty();
        }
        String key = command.toLowerCase(Locale.ROOT).replaceAll("[.!?]+$", "").trim();
        Intent i = exact.get(key);
        if (i != null) {
            return Optional.of(i);
        }
        if (key.startsWith("open ") && key.length() < 40 && !key.contains(" and ")) {
            String target = command.substring(5).replaceAll("[.!?]+$", "").trim();
            if (!target.isEmpty() && !target.contains("://")) {
                return Optional.of(new Intent("OPEN_APPLICATION", target));
            }
        }
        return Optional.empty();
    }

    public String toJson(Intent intent) {
        if (intent.target() == null || intent.target().isBlank()) {
            return """
                    {"actions":[{"type":"%s"}]}
                    """.formatted(intent.actionType());
        }
        String t = intent.target()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
        return """
                {"actions":[{"type":"%s","target":"%s"}]}
                """.formatted(intent.actionType(), t);
    }
}

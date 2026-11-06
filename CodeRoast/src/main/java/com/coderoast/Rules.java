package com.coderoast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

final class Util {
    static String pick(String... options) { return options[ThreadLocalRandom.current().nextInt(options.length)]; }
    static String snip(String line) { String t = line.trim(); return t.length() > 90 ? t.substring(0, 90) + "..." : t; }
    static boolean isComment(String line) {
        String t = line.trim();
        return t.startsWith("//") || t.startsWith("*") || t.startsWith("/*");
    }
}

/** Base class for rules that are just "regex on a line = roast". */
abstract class RegexRule implements Rule {
    private final String name;
    private final Pattern pattern;
    private final int penalty;
    private final String[] roasts;

    RegexRule(String name, String regex, int penalty, String... roasts) {
        this.name = name;
        this.pattern = Pattern.compile(regex);
        this.penalty = penalty;
        this.roasts = roasts;
    }

    boolean includeComments() { return false; }
    boolean skip(String line) { return false; }

    @Override
    public List<Finding> check(List<String> lines) {
        List<Finding> out = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!includeComments() && Util.isComment(line)) continue;
            if (skip(line)) continue;
            Matcher m = pattern.matcher(line);
            if (m.find()) {
                String arg = (m.groupCount() >= 1 && m.group(1) != null) ? m.group(1) : "";
                out.add(new Finding(i + 1, name, Util.snip(line), String.format(Util.pick(roasts), arg), penalty));
            }
        }
        return out;
    }
}

@Component
class SleepRule extends RegexRule {
    SleepRule() {
        super("Thread.sleep", "Thread\\.sleep\\(", 8,
            "Thread.sleep: the fix that just waits for the bug to feel better.",
            "Sleeping through a problem is not concurrency.");
    }
}

@Component
class PrintStackTraceRule extends RegexRule {
    PrintStackTraceRule() {
        super("printStackTrace", "\\.printStackTrace\\(", 7,
            "printStackTrace(): shouting the error into the void and hoping the void has a pager.");
    }
}

@Component
class EqualsBooleanRule extends RegexRule {
    EqualsBooleanRule() {
        super("== boolean", "==\\s*(true|false)\\b", 6,
            "== %s on a boolean. It was already a boolean; it didn't need a second opinion.");
    }
}

@Component
class TodoRule extends RegexRule {
    TodoRule() {
        super("TODO graveyard", "\\b(TODO|FIXME|HACK)\\b", 3,
            "%s: written as 'temporary' and still here. You and the file both know the truth.");
    }
    @Override boolean includeComments() { return true; }
}

@Component
class CommentedOutCodeRule extends RegexRule {
    CommentedOutCodeRule() {
        super("Commented-out code", "^\\s*//.*[;{}]\\s*$", 4,
            "Commented-out code. Git remembers everything, so you can let go.",
            "A museum of code you were too scared to delete.");
    }
    @Override boolean includeComments() { return true; }
}

@Component
class LongLineRule extends RegexRule {
    LongLineRule() {
        super("Long line", "^.{121,}$", 3,
            "This line needs its own scroll bar and emotional support.",
            "120+ characters. Somewhere, a code reviewer just sighed.");
    }
    @Override boolean includeComments() { return true; }
}

@Component
class BadNameRule extends RegexRule {
    BadNameRule() {
        super("Bad name",
            "\\b(?:int|long|double|float|boolean|String|Object|char|var|List<[^>]*>|Map<[^>]*>)\\s+(temp|tmp|data|foo|bar|baz|x|y|z|stuff|thing|asdf|obj|val|res|abc)\\s*[=;,)]",
            5,
            "'%s' is not a name, it's a shrug.",
            "A variable called '%s' is how mysteries begin.",
            "'%s'? Future you will have questions. Angry ones.");
    }
}

@Component
class MagicNumberRule extends RegexRule {
    MagicNumberRule() {
        super("Magic number", "(?<![\\w.\"])(\\d{3,})(?![\\w.\"])", 4,
            "%s appears out of nowhere. Give that number a name and some dignity.",
            "Magic number %s: a constant in witness protection.");
    }
    @Override boolean skip(String line) { return line.contains("final") || line.contains("@"); }
}

@Component
class EmptyCatchRule implements Rule {
    @Override
    public List<Finding> check(List<String> lines) {
        List<Finding> out = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (Util.isComment(line) || !line.contains("catch")) continue;
            boolean oneLiner = line.matches(".*catch\\s*\\(.*\\)\\s*\\{\\s*\\}.*");
            boolean nextCloses = line.trim().endsWith("{") && i + 1 < lines.size() && lines.get(i + 1).trim().equals("}");
            if (oneLiner || nextCloses) {
                out.add(new Finding(i + 1, "Empty catch", Util.snip(line),
                    Util.pick("An empty catch block. The exception knocked and you pretended not to be home.",
                              "Swallowing exceptions silently: bold move for something with no logs."), 12));
            }
        }
        return out;
    }
}

@Component
class PrintlnSpamRule implements Rule {
    private static final Pattern P = Pattern.compile("System\\.(out|err)\\.print");
    @Override
    public List<Finding> check(List<String> lines) {
        int count = 0, first = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (!Util.isComment(lines.get(i)) && P.matcher(lines.get(i)).find()) { count++; if (first < 0) first = i; }
        }
        if (count < 3) return List.of();
        String roast = String.format(Util.pick(
            "%d System.out.println calls. A logging framework would like a word.",
            "%d println calls. That's not debugging, that's a diary."), count);
        return List.of(new Finding(first + 1, "println spam", Util.snip(lines.get(first)), roast, Math.min(20, count * 2)));
    }
}

@Component
class DeepNestingRule implements Rule {
    @Override
    public List<Finding> check(List<String> lines) {
        List<Finding> out = new ArrayList<>();
        int depth = 0;
        for (int i = 0; i < lines.size() && out.size() < 3; i++) {
            String line = lines.get(i);
            if (Util.isComment(line)) continue;
            for (char c : line.toCharArray()) {
                if (c == '{') {
                    depth++;
                    if (depth == 5) {
                        out.add(new Finding(i + 1, "Deep nesting", Util.snip(line),
                            "Nesting level 5. You're not writing code, you're digging a mine.", 6));
                    }
                } else if (c == '}') depth = Math.max(0, depth - 1);
            }
        }
        return out;
    }
}

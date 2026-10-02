package com.coderoast;

import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class RoastEngine {
    private static final int MAX_PENALTY_PER_RULE = 25;
    private final List<Rule> rules;

    /** Spring injects every Rule bean, so adding a roast = adding one small class. */
    public RoastEngine(List<Rule> rules) { this.rules = rules; }

    public RoastResult roast(String code) {
        List<String> lines = Arrays.asList(code.split("\\R", -1));
        List<Finding> findings = new ArrayList<>();
        rules.forEach(r -> findings.addAll(r.check(lines)));
        findings.sort(Comparator.comparingInt(Finding::line));

        Map<String, Integer> perRule = new HashMap<>();
        findings.forEach(f -> perRule.merge(f.rule(), f.penalty(), Integer::sum));
        int penalty = perRule.values().stream().mapToInt(p -> Math.min(p, MAX_PENALTY_PER_RULE)).sum();
        int score = Math.max(0, 100 - penalty);

        String grade, title, verdict;
        if (score >= 90)      { grade = "A"; title = "Suspiciously Clean";      verdict = "Did you copy this from someone better?"; }
        else if (score >= 75) { grade = "B"; title = "Respectable Rookie";      verdict = "Not bad. I only winced a little."; }
        else if (score >= 55) { grade = "C"; title = "Junior Chaos Engineer";   verdict = "It probably works. Nobody knows why."; }
        else if (score >= 30) { grade = "D"; title = "Stack Overflow Tourist";  verdict = "This code has seen things."; }
        else                  { grade = "F"; title = "Certified Legacy Code";   verdict = "Please step away from the keyboard. Gently."; }
        if (findings.isEmpty()) verdict = "Nothing to roast. Either you're brilliant or you pasted a comment.";

        return new RoastResult(score, grade, title, verdict, lines.size(), findings);
    }
}

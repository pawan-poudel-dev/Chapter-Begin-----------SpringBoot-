package com.aidesktopagent.coding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StackTraceAnalyzerTest {

    @Test
    void explainsNullPointer() {
        String st = """
                Exception in thread "main" java.lang.NullPointerException: Cannot invoke "String.length()"
                \tat com.example.App.run(App.java:42)
                \tat com.example.App.main(App.java:10)
                """;
        var r = new StackTraceAnalyzer().analyze(st);
        assertTrue(r.getExceptionType().contains("NullPointer"));
        assertTrue(r.getExplanation().toLowerCase().contains("null"));
        assertNotNull(r.getPrimaryFrame());
        assertEquals(42, r.getPrimaryFrame().line());
        assertTrue(r.toHumanReport().contains("Next step"));
    }

    @Test
    void emptyInput() {
        var r = new StackTraceAnalyzer().analyze("  ");
        assertTrue(r.getExplanation().toLowerCase().contains("no error"));
    }

    @Test
    void classNotFound() {
        var r = new StackTraceAnalyzer().analyze("java.lang.ClassNotFoundException: com.foo.Bar");
        assertTrue(r.getExplanation().toLowerCase().contains("classpath")
                || r.getExplanation().toLowerCase().contains("missing"));
    }
}

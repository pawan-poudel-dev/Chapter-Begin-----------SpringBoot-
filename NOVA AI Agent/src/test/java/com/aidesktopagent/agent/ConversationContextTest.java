package com.aidesktopagent.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConversationContextTest {

    @Test
    void resolvesOrdinalResults() {
        ConversationContext ctx = new ConversationContext();
        ctx.setLastResults(List.of(
                new ConversationContext.SearchHit("a.java", "A"),
                new ConversationContext.SearchHit("b.java", "B")
        ));
        assertEquals("b.java", ctx.resolveReference("open the second result").orElseThrow());
        assertEquals("a.java", ctx.resolveReference("first result").orElseThrow());
    }

    @Test
    void resolvesThatFile() {
        ConversationContext ctx = new ConversationContext();
        ctx.setLastFile("src/Main.java");
        assertEquals("src/Main.java", ctx.resolveReference("open that file").orElseThrow());
    }
}

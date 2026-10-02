package com.coderoast;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class RoastEngineTest {

    @Test
    void flagsEmptyCatchBlock() {
        RoastEngine engine = new RoastEngine(List.of(new EmptyCatchRule()));
        RoastResult r = engine.roast("try {\n  run();\n} catch (Exception e) {\n}\n");
        assertEquals(1, r.findings().size());
        assertEquals(3, r.findings().get(0).line());
        assertTrue(r.score() < 100);
    }

    @Test
    void cleanCodeGetsPerfectScore() {
        RoastEngine engine = new RoastEngine(List.of(new SleepRule(), new EmptyCatchRule()));
        RoastResult r = engine.roast("int count = 0;\ncount++;");
        assertEquals(100, r.score());
        assertEquals("A", r.grade());
    }

    @Test
    void penaltyPerRuleIsCapped() {
        RoastEngine engine = new RoastEngine(List.of(new SleepRule()));
        String spam = "Thread.sleep(1);\n".repeat(20);
        assertEquals(75, engine.roast(spam).score()); // 100 - cap of 25
    }
}

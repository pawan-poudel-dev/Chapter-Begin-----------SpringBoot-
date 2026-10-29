package com.aidesktopagent.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoggingServiceTest {

    @Test
    void forComponentCreatesInstance() {
        LoggingService svc = LoggingService.forComponent("test-component");
        assertNotNull(svc);
    }

    @Test
    void applicationLoggerIsAvailable() {
        LoggingService svc = LoggingService.application();
        assertNotNull(svc);
    }

    @Test
    void sanitizeRedactsApiKeyPatterns() {
        String input = "Calling API with api_key=sk-abc123def456 and more text";
        String result = LoggingService.sanitize(input);
        assertTrue(result.contains("[REDACTED]"));
        assertFalse(result.contains("sk-abc123def456"));
    }

    @Test
    void sanitizeRedactsPasswordPatterns() {
        String input = "user=admin password=supersecret123";
        String result = LoggingService.sanitize(input);
        assertTrue(result.contains("[REDACTED]"));
        assertFalse(result.contains("supersecret123"));
    }

    @Test
    void sanitizeRedactsBearerTokens() {
        String input = "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload";
        String result = LoggingService.sanitize(input);
        assertTrue(result.contains("[REDACTED]"));
    }

    @Test
    void sanitizeHandlesNull() {
        assertNull(LoggingService.sanitize(null));
    }

    @Test
    void sanitizeLeavesCleanMessagesUntouched() {
        String clean = "Action planned: open browser tab";
        assertEquals(clean, LoggingService.sanitize(clean));
    }

    @Test
    void lifecycleMethodsDoNotThrow() {
        LoggingService svc = LoggingService.forComponent("unit-test");
        assertDoesNotThrow(() -> {
            svc.startup("starting test");
            svc.commandReceived("say hello");
            svc.actionPlanned("act-1", "print greeting");
            svc.actionValidated("act-1", true);
            svc.actionExecuted("act-1", "ok");
            svc.verification("act-1", true, "output matched");
            svc.failure("unit", "simulated failure");
            svc.recovery("unit", "retried once");
            svc.shutdown("done");
        });
    }

    @Test
    void failureWithThrowableDoesNotThrow() {
        LoggingService svc = LoggingService.forComponent("unit-test");
        assertDoesNotThrow(() ->
                svc.failure("boom", new RuntimeException("test exception"))
        );
    }

    @Test
    void genericLogMethodsDoNotThrow() {
        LoggingService svc = LoggingService.forComponent("unit-test");
        assertDoesNotThrow(() -> {
            svc.info("info msg");
            svc.debug("debug msg");
            svc.warn("warn msg");
            svc.error("error msg");
            svc.error("error with cause", new IllegalStateException("cause"));
        });
    }
}

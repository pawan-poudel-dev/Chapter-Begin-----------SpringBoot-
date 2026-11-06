package com.aidesktopagent.security;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RetryPolicyTest {

    @Test
    void retriesThenSucceeds() throws Exception {
        AtomicInteger n = new AtomicInteger();
        RetryPolicy p = new RetryPolicy(3, 1, e -> true);
        String v = p.execute(() -> {
            if (n.incrementAndGet() < 2) {
                throw new RuntimeException("timeout");
            }
            return "ok";
        });
        assertEquals("ok", v);
        assertEquals(2, n.get());
    }

    @Test
    void nonRetryableFailsFast() {
        RetryPolicy p = new RetryPolicy(5, 1, e -> false);
        assertThrows(RuntimeException.class, () -> p.execute(() -> {
            throw new RuntimeException("boom");
        }));
    }
}

package com.aidesktopagent.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.LoggerFactory;

/**
 * Minimal logging bootstrap.
 * Logback is configured primarily via logback.xml on the classpath;
 * this class provides a programmatic safety net if needed.
 */
public final class LoggingBootstrap {

    private LoggingBootstrap() {
        // utility class
    }

    /**
     * Ensures the logging system is initialized.
     * Safe to call multiple times.
     */
    public static void init() {
        // Trigger Logback initialization by obtaining the root logger context.
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        if (context.getLogger("ROOT").getLevel() == null) {
            context.getLogger("ROOT").setLevel(Level.INFO);
        }
    }
}

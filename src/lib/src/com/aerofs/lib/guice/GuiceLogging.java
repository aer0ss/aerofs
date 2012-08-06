package com.aerofs.lib.guice;

import java.util.logging.*;

public class GuiceLogging
{
    private static final Handler HANDLER;

    static {
        HANDLER = new StreamHandler(System.out, new Formatter() {
            @Override
            public String format(LogRecord record) {
                return String.format("[Guice %s] %s%n",
                        record.getLevel().getName(),
                        record.getMessage());
            }
        });
        HANDLER.setLevel(Level.ALL);
    }

    public static Logger getLogger() {
        return Logger.getLogger("com.google.inject");
    }

    public static void enable() {
        Logger guiceLogger = getLogger();
        guiceLogger.addHandler(HANDLER);
        guiceLogger.setLevel(Level.ALL);
    }

    public static void disable() {
        Logger guiceLogger = getLogger();
        guiceLogger.setLevel(Level.OFF);
        guiceLogger.removeHandler(HANDLER);
    }
}

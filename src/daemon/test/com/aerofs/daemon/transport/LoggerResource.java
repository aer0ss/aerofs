package com.aerofs.daemon.transport;/*
 * Copyright (c) Air Computing Inc., 2013.
 */

import com.aerofs.base.Loggers;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.lib.log.LogUtil.Level;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;

public final class LoggerResource extends ExternalResource
{
    static {
        Level level = Level.NONE;

        String specifiedLevel = System.getProperty("com.aerofs.test.logLevel");
        if (specifiedLevel != null && specifiedLevel.equalsIgnoreCase("DEBUG")) {
            level = Level.DEBUG;
        }

        LogUtil.enableConsoleLogging();
        LogUtil.setLevel(level);
    }

    private final Logger logger;

    public LoggerResource(Class<?> klass)
    {
        logger = Loggers.getLogger(klass);
    }

    public Logger l()
    {
        return logger;
    }
}
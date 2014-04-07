/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.testlib;

import com.aerofs.lib.log.LogUtil;
import com.aerofs.lib.log.LogUtil.Level;

public final class LoggerSetup
{
    static {
        Level level = Level.NONE;

        String specifiedLevel = System.getProperty("com.aerofs.test.logLevel");
        if (specifiedLevel != null && specifiedLevel.equalsIgnoreCase("DEBUG")) {
            level = Level.DEBUG;
        }

        LogUtil.setLevel(level);
        LogUtil.enableConsoleLogging();
    }

    public static void init()
    {
        // exists only so that callers have something to call to run the static initializer above
    }
}
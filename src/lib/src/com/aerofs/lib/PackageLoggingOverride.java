/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

// TODO: remove this class
public abstract class PackageLoggingOverride
{
    protected PackageLoggingOverride()
    {
        // empty
    }

    protected static void overrideLogLevels(Class<?>[] classes, Level desiredLevel)
    {
        Level rootLogLevel = Logger.getRootLogger().getLevel();
        Level currentLevel;
        Logger logger;
        for (Class<?> c : classes) {
            if (c != null) {
                logger = Logger.getLogger(c);
                currentLevel = logger.getLevel() == null ? rootLogLevel : logger.getLevel();
                if (currentLevel.isGreaterOrEqual(desiredLevel)) {
                    logger.setLevel(desiredLevel);
                }
            }
        }
    }
}

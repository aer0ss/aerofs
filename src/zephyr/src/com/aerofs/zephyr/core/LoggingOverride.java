/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.zephyr.core;

import com.aerofs.lib.PackageLoggingOverride;
import org.apache.log4j.Level;

// TODO: remove this class
public class LoggingOverride extends PackageLoggingOverride
{
    private LoggingOverride()
    {
        // private to enforce uninstantiability
    }

    public static synchronized void setLogLevels_()
    {
        if (_set) return;

        Class<?>[] classes = new Class<?>[] {
            IIOEventHandler.class,
            BufferPool.class,
            Dispatcher.class,
            ZUtil.class,
            ExAlreadyBound.class,
            FatalIOEventHandlerException.class
        };

        overrideLogLevels(classes, Level.INFO);

        _set = true;
    }

    private static boolean _set = false;
}

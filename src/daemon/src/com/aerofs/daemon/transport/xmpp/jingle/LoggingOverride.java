/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.transport.xmpp.jingle;

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
            IJingle.class,
            IProxyObjectContainer.class,
            ISignalThreadTask.class,
            Channel.class,
            Engine.class,
            Jingle.class,
            SendData.class,
            SignalThread.class,
            Tandem.class
        };

        overrideLogLevels(classes, Level.INFO);

        _set = true;
    }

    private static boolean _set = false;
}

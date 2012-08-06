package com.aerofs.daemon.core.linker.scanner;

import org.apache.log4j.Level;

import com.aerofs.lib.PackageLoggingOverride;

//TODO: remove this class
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
            ScanSession.class,
            ScanSessionQueue.class
        };

        overrideLogLevels(classes, Level.INFO);

        _set = true;
    }

    private static boolean _set = false;
}

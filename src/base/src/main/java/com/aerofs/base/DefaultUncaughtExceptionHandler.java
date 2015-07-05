/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.base;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.Thread.UncaughtExceptionHandler;

public final class DefaultUncaughtExceptionHandler implements UncaughtExceptionHandler
{
    private static final Logger l = LoggerFactory.getLogger(DefaultUncaughtExceptionHandler.class);

    @Override
    public void uncaughtException(Thread thread, Throwable throwable)
    {
        try {
            l.error("uncaught exception thd:{} err:{} - kill system",
                    thread.getName(), throwable, throwable);
        } catch (Throwable t) {
            // thou shalt not prevent the process from being killed!
        }
        System.exit(0x4655434b);
    }
}

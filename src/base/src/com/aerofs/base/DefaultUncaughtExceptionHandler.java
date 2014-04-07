/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.base;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.Thread.UncaughtExceptionHandler;

// copy of the original from verkehr
public final class DefaultUncaughtExceptionHandler implements UncaughtExceptionHandler
{
    private static final Logger l = LoggerFactory.getLogger(DefaultUncaughtExceptionHandler.class);

    @Override
    public void uncaughtException(Thread thread, Throwable throwable)
    {
        l.error("uncaught exception thd:{} err:{} - kill system", thread.getName(), throwable, throwable);
        System.exit(0x4655434b);
    }
}

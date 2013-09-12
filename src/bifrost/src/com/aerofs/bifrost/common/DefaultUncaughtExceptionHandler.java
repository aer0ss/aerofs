/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.bifrost.common;

import com.aerofs.base.Loggers;
import org.slf4j.Logger;

import java.lang.Thread.UncaughtExceptionHandler;

public final class DefaultUncaughtExceptionHandler implements UncaughtExceptionHandler
{
    @Override
    public void uncaughtException(Thread thread, Throwable throwable)
    {
        l.error("uncaught exception thd:{} err:{} - kill system", thread.getName(), throwable, throwable);
        System.exit(UNCAUGHT_EXCEPTION_EXIT_CODE);
    }

    private static final Logger l = Loggers.getLogger(DefaultUncaughtExceptionHandler.class);
    private static final int UNCAUGHT_EXCEPTION_EXIT_CODE = 99;
}

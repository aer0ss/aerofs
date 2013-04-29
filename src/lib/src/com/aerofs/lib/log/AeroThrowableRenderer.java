/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.log;

import org.apache.log4j.DefaultThrowableRenderer;
import org.apache.log4j.spi.ThrowableRenderer;

import static com.aerofs.lib.Util.shouldPrintStackTrace;

class AeroThrowableRenderer implements ThrowableRenderer
{
    private final ThrowableRenderer _default = new DefaultThrowableRenderer();

    @Override
    public String[] doRender(Throwable t)
    {
        if (shouldPrintStackTrace(t)) {
            return _default.doRender(t);
        } else {
            return new String[]{t.toString()};
        }
    }
}
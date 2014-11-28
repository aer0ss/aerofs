/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Loggers
{
    private Loggers()
    {
        // private to enforce uninstantiability
    }

    static
    {
        org.jboss.netty.logging.InternalLoggerFactory.setDefaultFactory(new org.jboss.netty.logging.Slf4JLoggerFactory());
    }

    /**
     * call to ensure static initializers have run
     */
    public static void init()
    {
    }

    /**
     * @return return a logger for class {@code cls}
     */
    public static Logger getLogger(Class<?> cls)
    {
        return LoggerFactory.getLogger(cls);
    }
}

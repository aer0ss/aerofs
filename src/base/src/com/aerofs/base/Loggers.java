/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.base;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Loggers
{
    private Loggers() {}

    static {
        org.jboss.netty.logging.InternalLoggerFactory.setDefaultFactory(
                new org.jboss.netty.logging.Slf4JLoggerFactory());
    }

    /// ensure static initializers have run
    public static void init()
    {
    }

    public static Logger getLogger(Class<?> cls)
    {
        return LoggerFactory.getLogger(cls);
    }
}

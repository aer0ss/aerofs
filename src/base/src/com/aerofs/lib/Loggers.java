/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib;

import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.logging.Slf4JLoggerFactory;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Loggers
{
    private Loggers() {}

    private static final String ANDROID_LOGGER_CLASS = "org.slf4j.impl.AndroidLoggerFactory";
    private static final boolean isAndroid;

    static final int TAG_MAX_LENGTH = 23;

    static {
        Class<? extends ILoggerFactory> cls = LoggerFactory.getILoggerFactory().getClass();
        isAndroid = ANDROID_LOGGER_CLASS.equals(cls.getName());
    }

    static {
        InternalLoggerFactory.setDefaultFactory(new Slf4JLoggerFactory());
    }

    public static Logger getLogger(Class<?> cls)
    {
        if (isAndroid) {
            String name = "aerofs." + cls.getSimpleName();
            if (name.length() > TAG_MAX_LENGTH) {
                name = name.substring(0, TAG_MAX_LENGTH - 1) + '…';
            }
            return LoggerFactory.getLogger(name);
        } else {
            return LoggerFactory.getLogger(cls);
        }
    }
}

/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Loggers
{
    private Loggers() {}

    public static Logger getLogger(Class<?> cls)
    {
        return LoggerFactory.getLogger(cls);
    }
}

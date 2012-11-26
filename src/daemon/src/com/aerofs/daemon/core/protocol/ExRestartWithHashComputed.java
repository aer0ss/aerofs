/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.protocol;

public class ExRestartWithHashComputed extends Exception
{
    private static final long serialVersionUID = 1L;

    public ExRestartWithHashComputed(String msg)
    {
        super(msg);
    }
}

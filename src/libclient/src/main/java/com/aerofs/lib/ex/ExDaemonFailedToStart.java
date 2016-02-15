/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.ex;

/**
 * Exception wrapper which causes extra information to be added to defects
 */
public class ExDaemonFailedToStart extends Exception
{
    private static final long serialVersionUID = 1L;

    public ExDaemonFailedToStart(Throwable cause)
    {
        super(cause);
    }
}

/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.controller;

public class ExLaunchAborted extends Exception
{
    private static final long serialVersionUID = 1L;

    public ExLaunchAborted()
    {
        super();
    }

    public ExLaunchAborted(String string)
    {
        super(string);
    }

    public ExLaunchAborted(Throwable cause)
    {
        super(cause);
    }
}

/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.lib.ex;

/**
 * Root of all fatal exceptions in AeroFS.
 */
public class ExFatal extends Error
{
    private static final long serialVersionUID = 3007890631132798734L;

    public ExFatal(Throwable throwable)
    {
        super(throwable);
    }

    public ExFatal(String message)
    {
        super(message);
    }

    // private to prevent subclasses from overriding
    @SuppressWarnings("unused")
    private ExFatal(String s, Throwable throwable)
    {
        super(s, throwable);
    }

    // private to prevent subclasses from overriding
    @SuppressWarnings("unused")
    private ExFatal()
    {
    }
}

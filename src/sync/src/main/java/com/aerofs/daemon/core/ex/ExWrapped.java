/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.ex;

/**
 * Base class for semantic wrapping of exceptions
 */
public class ExWrapped extends Exception
{
    private static final long serialVersionUID = -0L;

    public final Exception _e;

    protected ExWrapped(Exception e)
    {
        super(e);
        _e = e;
    }

    public Exception unwrap()
    {
        return _e;
    }

    public Exception recursiveUnwrap()
    {
        return _e instanceof ExWrapped ? ((ExWrapped)_e).recursiveUnwrap() : _e;
    }
}

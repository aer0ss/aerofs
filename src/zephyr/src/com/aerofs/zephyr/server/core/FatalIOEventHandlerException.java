/**
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.zephyr.server.core;

/**
 * Throw this exception to signal that we should fatal (terminate) the entire
 * {@link com.aerofs.zephyr.server.core.Dispatcher} operation
 */
public class FatalIOEventHandlerException extends Exception
{
    public FatalIOEventHandlerException(String msg)
    {
        super(msg);
    }

    public FatalIOEventHandlerException(String msg, Throwable cause)
    {
        super(msg, cause);
    }

    private static final long serialVersionUID = 1L;
}

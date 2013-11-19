/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport;

public abstract class ExTransport extends Exception
{
    private static final long serialVersionUID = 1L;

    public ExTransport(String message)
    {
        super(message);
    }

    public ExTransport(String message, Throwable cause)
    {
        super(message, cause);
    }
}

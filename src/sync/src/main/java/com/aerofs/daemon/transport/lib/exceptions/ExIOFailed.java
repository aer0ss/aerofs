/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib.exceptions;

public final class ExIOFailed extends ExTransport
{
    private static final long serialVersionUID = 1L;

    public ExIOFailed(String message)
    {
        super(message);
    }

    public ExIOFailed(String message, Throwable cause)
    {
        super(message, cause);
    }
}

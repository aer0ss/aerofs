/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport;

public final class ExIOOFailed extends ExTransport
{
    private static final long serialVersionUID = 1L;

    public ExIOOFailed(String message)
    {
        super(message);
    }

    public ExIOOFailed(String message, Throwable cause)
    {
        super(message, cause);
    }
}

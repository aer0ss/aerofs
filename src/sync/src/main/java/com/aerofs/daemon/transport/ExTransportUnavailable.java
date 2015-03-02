/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport;

public final class ExTransportUnavailable extends ExTransport
{
    private static final long serialVersionUID = 1L;

    public ExTransportUnavailable(String message)
    {
        super(message);
    }

    public ExTransportUnavailable(String message, Throwable cause)
    {
        super(message, cause);
    }
}

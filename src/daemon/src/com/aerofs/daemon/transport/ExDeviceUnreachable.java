/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport;

public final class ExDeviceUnreachable extends ExTransport
{
    private static final long serialVersionUID = 1L;

    public ExDeviceUnreachable(String message)
    {
        super(message);
    }

    public ExDeviceUnreachable(String message, Throwable cause)
    {
        super(message, cause);
    }
}

/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.exception;

public final class ExDeviceDisconnected extends ExTransport
{
    private static final long serialVersionUID = 1L;

    public ExDeviceDisconnected(String message)
    {
        super(message);
    }

    public ExDeviceDisconnected(String message, Throwable cause)
    {
        super(message, cause);
    }
}

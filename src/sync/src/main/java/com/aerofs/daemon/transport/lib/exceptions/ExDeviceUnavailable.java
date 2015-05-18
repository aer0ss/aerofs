/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib.exceptions;

public final class ExDeviceUnavailable extends ExTransport
{
    private static final long serialVersionUID = 1L;

    public ExDeviceUnavailable(String message)
    {
        super(message);
    }

    public ExDeviceUnavailable(String message, Throwable cause)
    {
        super(message, cause);
    }
}

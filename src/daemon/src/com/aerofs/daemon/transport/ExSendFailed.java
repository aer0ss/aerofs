/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport;

public final class ExSendFailed extends ExTransport
{
    private static final long serialVersionUID = 1L;

    public ExSendFailed(String message)
    {
        super(message);
    }

    public ExSendFailed(String message, Throwable cause)
    {
        super(message, cause);
    }
}

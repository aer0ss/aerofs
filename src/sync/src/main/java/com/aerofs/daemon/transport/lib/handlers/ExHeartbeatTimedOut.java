/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.lib.handlers;

import com.aerofs.daemon.transport.lib.exceptions.ExTransport;

/**
 * Thrown when the Zephyr heartbeat mechanism times out.
 */
public final class ExHeartbeatTimedOut extends ExTransport
{
    private static final long serialVersionUID = 8476512524483144516L;

    public ExHeartbeatTimedOut(String message)
    {
        super(message);
    }

    public ExHeartbeatTimedOut(String message, Throwable cause)
    {
        super(message, cause);
    }
}

/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.pulse.ex;

import com.aerofs.daemon.tng.ex.ExTransport;

public class ExInvalidPulseMessage extends ExTransport
{
    private static final long serialVersionUID = 1L;

    public ExInvalidPulseMessage(String message)
    {
        super("Pulse call message was invalid: " + message);
    }
}

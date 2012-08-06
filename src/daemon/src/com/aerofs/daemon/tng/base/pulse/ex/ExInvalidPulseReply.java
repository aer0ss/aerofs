/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.pulse.ex;

import com.aerofs.daemon.tng.ex.ExTransport;

public class ExInvalidPulseReply extends ExTransport
{
    private static final long serialVersionUID = 1L;

    public ExInvalidPulseReply(int expectedPulseId, int actualPulseId)
    {
        super("Pulse reply was invalid: got " + expectedPulseId + " but expected " + actualPulseId);
    }
}

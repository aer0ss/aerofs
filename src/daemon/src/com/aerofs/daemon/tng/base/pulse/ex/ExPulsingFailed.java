/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.base.pulse.ex;

import com.aerofs.daemon.tng.ex.ExTransport;

public class ExPulsingFailed extends ExTransport
{
    private static final long serialVersionUID = 1L;

    public ExPulsingFailed()
    {
        this("Peer did not respond to pulse");
    }

    public ExPulsingFailed(String cause)
    {
        super(cause);
    }
}

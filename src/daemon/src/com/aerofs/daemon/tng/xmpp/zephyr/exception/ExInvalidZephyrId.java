/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.xmpp.zephyr.exception;

import com.aerofs.daemon.tng.ex.ExTransport;

public class ExInvalidZephyrId extends ExTransport
{
    private static final long serialVersionUID = 1L;

    public ExInvalidZephyrId(int badZid)
    {
        super("Bad zid (" + badZid + ") received");
    }
}

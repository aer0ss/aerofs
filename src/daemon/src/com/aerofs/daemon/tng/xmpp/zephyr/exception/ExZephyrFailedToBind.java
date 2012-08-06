/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.xmpp.zephyr.exception;

import com.aerofs.daemon.tng.ex.ExTransport;

public class ExZephyrFailedToBind extends ExTransport
{
    private static final long serialVersionUID = 1L;

    public ExZephyrFailedToBind()
    {
        super("Zephyr could not bind to server");
    }

}

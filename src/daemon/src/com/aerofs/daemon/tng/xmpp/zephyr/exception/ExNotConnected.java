/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.xmpp.zephyr.exception;

import com.aerofs.daemon.tng.base.IUnicastConnection;
import com.aerofs.daemon.tng.ex.ExTransport;

public class ExNotConnected extends ExTransport
{
    private static final long serialVersionUID = 1L;

    public ExNotConnected(IUnicastConnection connection)
    {
        super("The connection " + connection.toString() + " is not connected");
    }
}

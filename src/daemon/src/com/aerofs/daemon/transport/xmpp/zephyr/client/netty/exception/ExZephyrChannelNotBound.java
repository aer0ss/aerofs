package com.aerofs.daemon.transport.xmpp.zephyr.client.netty.exception;

import com.aerofs.base.id.DID;

public class ExZephyrChannelNotBound extends Exception {

    private static final long serialVersionUID = 1L;

    public ExZephyrChannelNotBound(DID did)
    {
        super("Zephyr channel to DID" + did + " is not yte bound");
    }
}

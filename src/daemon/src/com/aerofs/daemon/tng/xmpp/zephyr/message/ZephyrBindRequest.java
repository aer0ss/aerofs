/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.xmpp.zephyr.message;

public class ZephyrBindRequest implements IZephyrMessage
{
    public final int remoteZid;

    public ZephyrBindRequest(int remoteZid)
    {
        this.remoteZid = remoteZid;
    }
}

package com.aerofs.daemon.transport.xmpp.zephyr.client.netty.message;

public class ZephyrBindRequest {
    public final int remoteZid;

    public ZephyrBindRequest(int remoteZid)
    {
        this.remoteZid = remoteZid;
    }
}

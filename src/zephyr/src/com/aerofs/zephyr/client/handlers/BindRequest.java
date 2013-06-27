package com.aerofs.zephyr.client.handlers;

import javax.annotation.concurrent.Immutable;

@Immutable
final class BindRequest
{
    public final int remoteZid;

    public BindRequest(int remoteZid)
    {
        this.remoteZid = remoteZid;
    }
}

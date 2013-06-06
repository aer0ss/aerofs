package com.aerofs.zephyr.client.message;

import javax.annotation.concurrent.Immutable;

@Immutable
public final class BindRequest
{
    public final int remoteZid;

    public BindRequest(int remoteZid)
    {
        this.remoteZid = remoteZid;
    }
}

package com.aerofs.zephyr.client.message;

import org.jboss.netty.buffer.ChannelBuffer;

import javax.annotation.concurrent.Immutable;

@Immutable
public final class RelayedData
{
    private final ChannelBuffer payload;

    public RelayedData(ChannelBuffer payload)
    {
        this.payload = payload;
    }

    public ChannelBuffer getPayload()
    {
        return payload;
    }
}

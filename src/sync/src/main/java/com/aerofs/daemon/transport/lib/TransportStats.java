/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import java.util.concurrent.atomic.AtomicLong;

public final class TransportStats
{
    private AtomicLong bytesSent = new AtomicLong(0);
    private AtomicLong bytesReceived = new AtomicLong(0);

    public void addBytesReceived(long count)
    {
        bytesSent.getAndAdd(count);
    }

    public long getBytesReceived()
    {
        return bytesSent.get();
    }

    public void addBytesSent(long count)
    {
        bytesReceived.getAndAdd(count);
    }

    public long getBytesSent()
    {
        return bytesReceived.get();
    }
}

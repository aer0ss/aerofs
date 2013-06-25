/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.xmpp.zephyr.netty;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.exception.ExDeviceDisconnected;
import com.aerofs.zephyr.client.IZephyrChannelStats;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkArgument;

final class ZephyrAttachment
{
    private static final ExDeviceDisconnected DEFAULT = new ExDeviceDisconnected("disconnected");

    private final String id;
    private final DID remote;
    private final IZephyrChannelStats channelStats;
    private final AtomicBoolean notifyListener = new AtomicBoolean(true); // notify by default!
    private final AtomicReference<Exception> disconnectCause = new AtomicReference<Exception>(DEFAULT);

    ZephyrAttachment(String id, DID remote, IZephyrChannelStats channelStats)
    {
        this.id = id;
        this.remote = remote;
        this.channelStats = channelStats;
    }

    String getId()
    {
        return id;
    }

    DID getRemote()
    {
        return remote;
    }

    IZephyrChannelStats getChannelStats()
    {
        return channelStats;
    }

    boolean shouldNotifyListener()
    {
        return notifyListener.get();
    }

    Exception getDisconnectCause() // FIXME (AG): use this to notify the upper layer
    {
        return disconnectCause.get();
    }

    void setDisconnectParameters(Exception disconnectCause, boolean notifyListener)
    {
        if (this.disconnectCause.getAndSet(disconnectCause) == DEFAULT) {
            boolean previous = this.notifyListener.getAndSet(notifyListener);
            checkArgument(disconnectCause != DEFAULT);
            checkArgument(previous, "disconnect parameters already modified");
        }
    }
}

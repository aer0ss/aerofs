/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.zephyr.client.pipeline;

import com.aerofs.zephyr.client.IZephyrChannelStats;
import com.aerofs.zephyr.client.IZephyrSignallingClient;
import org.jboss.netty.channel.Channel;

import javax.annotation.Nullable;

public abstract class ZephyrPipeline
{
    static final String RELAYED_HANDLER_NAME = "relayed";

    private ZephyrPipeline()
    {
        // private to prevent instantiation
    }

    public static @Nullable IZephyrSignallingClient getZephyrSignallingClient(Channel channel)
    {
        return channel.getPipeline().get(ZephyrHandshakeHandler.class);
    }

    public static @Nullable IZephyrChannelStats getZephyrChannelStats(Channel channel)
    {
        return channel.getPipeline().get(ChannelStatsHandler.class);
    }

    public static boolean hasHandshakeCompleted(Channel channel)
    {
        return channel.getPipeline().get(ZephyrHandshakeHandler.class) == null; // absence of handler indicates handshaking finished
    }

    public static String getRelayedHandlerName()
    {
        return RELAYED_HANDLER_NAME;
    }
}

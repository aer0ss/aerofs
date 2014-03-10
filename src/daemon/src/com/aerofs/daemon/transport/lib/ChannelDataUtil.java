/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.lib;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class ChannelDataUtil
{
    private ChannelDataUtil() { } // private to protect instantiation

    public static IChannelData getChannelData(ChannelEvent e)
    {
        return getChannelData(e.getChannel());
    }

    public static IChannelData getChannelData(Channel channel)
    {
        IChannelData channelData = (IChannelData) channel.getAttachment();

        checkNotNull(channelData);
        checkNotNull(channelData.getRemoteDID());
        checkNotNull(channelData.getRemoteUserID());

        return channelData;
    }

    // FIXME (AG): these two methods are very similar
    public static boolean isChannelConnected(Channel channel)
    {
        // Note (GS): In an ideal world, the CNameVerificationHandler would do some magic so that
        // channel.isChannelConnected() returns false until the CName is verified. Unfortunately, I
        // haven't found a way to do that. I'm able to delay firing the channelConnected event until
        // the CName is verified, but I can't make channel.isConnected() lie to you. So,
        // we perform a two-stage check:
        //
        // 1. we check if an attachment exists (TCP compatibility)
        // 2. if an attachment exists, we check if the remote id is set (this only happens after cname verification)
        //
        // only if both are true do we know that the channel is connected

        if (channel.getAttachment() == null) return false;

        IChannelData channelData = (IChannelData) channel.getAttachment();
        return channelData.getRemoteUserID() != null;
    }

    public static boolean hasValidChannelData(Channel channel)
    {
        if (channel.getAttachment() == null) {
            return false;
        }

        if (!(channel.getAttachment() instanceof IChannelData)) {
            return false;
        }

        IChannelData channelData = (IChannelData) channel.getAttachment();
        return channelData.getRemoteDID() != null && channelData.getRemoteUserID() != null;
    }
}

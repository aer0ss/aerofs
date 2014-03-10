/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.id.DID;
import org.jboss.netty.channel.Channel;

/**
*/
public interface IIncomingChannelListener
{
    /**
     * Called when we have a connected channel to receive data from a remote peer
     */
    void onIncomingChannel(DID did, Channel channel);
}

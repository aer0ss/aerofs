/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.ids.DID;
import org.jboss.netty.channel.Channel;

public interface ChannelRegisterer
{
    /**
     * Called when we have a connected channel to receive data from a remote peer
     */
    boolean registerChannel(Channel channel, DID did);
}

/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.ExDeviceUnavailable;
import com.aerofs.daemon.transport.ExTransportUnavailable;
import org.jboss.netty.channel.ChannelFuture;

/**
 * This type can create and connect outbound unicast channel instances.
 */
public interface IUnicastConnector
{
    /**
     * Attempt to create a channel for the given Device using whatever addressing information we happen to have.
     */
    ChannelFuture newChannel(final DID did)
            throws ExTransportUnavailable, ExDeviceUnavailable;
}
/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.ids.DID;
import com.aerofs.daemon.transport.lib.exceptions.ExDeviceUnavailable;
import com.aerofs.daemon.transport.lib.exceptions.ExTransportUnavailable;
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

    /**
     * Attempt to create a channel for the given Device using the given Presence Location.
     * @param presenceLocation the presence locations to connect to (contains the DID + the location)
     */
    ChannelFuture newChannel(IPresenceLocation presenceLocation);
}

/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.havre;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipeline;

import javax.annotation.Nullable;

public interface EndpointConnector
{
    /**
     * Open a client connection to a REST endpoint on behalf of a given user
     *
     * @param user user for which the endpoint should be suitable
     * @param did prefered endpoint, if not null
     * @param strictMatch whether to fail if the prefered endpoint is not available
     * @param pipeline pipeline to place on top of the channel
     * @return a Netty channel connected to a suitable endpoint, null if none is found
     */
    public @Nullable Channel connect(UserID user, @Nullable DID did, boolean strictMatch,
            ChannelPipeline pipeline);

    /**
     * @return the device to which a channel obtained from {@link #connect} is associated
     */
    public DID device(Channel channel);
}

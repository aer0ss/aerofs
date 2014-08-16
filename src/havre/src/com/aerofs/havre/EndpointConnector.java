/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.havre;

import com.aerofs.base.id.DID;
import com.aerofs.oauth.AuthenticatedPrincipal;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipeline;

import javax.annotation.Nullable;

public interface EndpointConnector
{
    /**
     * Open a client connection to a REST endpoint on behalf of a given user
     *
     * NB: MUST be threadsafe
     *
     * @param principal authenticated user for which the endpoint should be suitable
     * @param did prefered endpoint, if not null
     * @param strictMatch whether to fail if the prefered endpoint is not available
     * @param minVersion minimum endpoint version required to service incoming request
     * @param pipeline pipeline to place on top of the channel
     * @return a Netty channel connected to a suitable endpoint, null if none is found
     */
    public @Nullable Channel connect(AuthenticatedPrincipal principal, @Nullable DID did,
            boolean strictMatch, @Nullable Version minVersion, ChannelPipeline pipeline);

    /**
     * NB: MUST be threadsafe
     *
     * @return the device to which a channel obtained from {@link #connect} is associated
     */
    public DID device(Channel channel);

    public Iterable<DID> alternateDevices(Channel channel);
}

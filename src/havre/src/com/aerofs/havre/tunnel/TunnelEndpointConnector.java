/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.havre.tunnel;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.havre.EndpointConnector;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.aerofs.tunnel.ITunnelConnectionListener;
import com.aerofs.tunnel.TunnelAddress;
import com.aerofs.tunnel.TunnelHandler;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipeline;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Keeps track of tunnel connections from any number of {@link com.aerofs.tunnel.TunnelServer} and
 * offers an {@link EndpointConnector} interface to create virtual connections multiplexed on the
 * underlying physical tunnel connections.
 */
public class TunnelEndpointConnector implements ITunnelConnectionListener, EndpointConnector
{
    // TODO: use ThreadLocalRandom when we switch to Java 7
    private final Random _random = new Random();
    private final Map<UserID, Map<DID, TunnelHandler>> _endpointsByUser = Maps.newHashMap();

    @Override
    public synchronized @Nullable Channel connect(AuthenticatedPrincipal principal, DID did,
            boolean strictMatch, ChannelPipeline pipeline)
    {
        TunnelHandler tunnel = getEndpoint(principal, did, strictMatch);
        return tunnel != null ? tunnel.newVirtualChannel(pipeline) : null;
    }

    @Override
    public DID device(Channel channel)
    {
        return ((TunnelAddress)channel.getRemoteAddress()).did;
    }

    @Override
    public synchronized void tunnelOpen(TunnelAddress addr, TunnelHandler handler)
    {
        Map<DID, TunnelHandler> connectedDevices = _endpointsByUser.get(addr.user);
        if (connectedDevices == null) {
            connectedDevices = Maps.newHashMap();
            _endpointsByUser.put(addr.user, connectedDevices);
        }
        connectedDevices.put(addr.did, handler);
    }

    @Override
    public synchronized void tunnelClosed(TunnelAddress addr, TunnelHandler handler)
    {
        Map<DID, TunnelHandler> connectedDevices = _endpointsByUser.get(addr.user);
        if (connectedDevices != null && connectedDevices.get(addr.did) == handler) {
            connectedDevices.remove(addr.did);
        }
    }

    private @Nullable TunnelHandler getEndpoint(AuthenticatedPrincipal principal, DID did,
            boolean strictMatch)
    {
        TunnelHandler handler = getEndpoint(principal.getUserID(), did, strictMatch);
        return handler != null
                ? handler
                : getEndpoint(principal.getOrganizationID().toTeamServerUserID(), did, strictMatch);
    }

    private @Nullable TunnelHandler getEndpoint(UserID user, DID did, boolean strictMatch)
    {
        TunnelAddress addr = new TunnelAddress(user, did);
        Map<DID, TunnelHandler> connectedDevices = _endpointsByUser.get(addr.user);
        if (connectedDevices == null || connectedDevices.isEmpty()) return null;

        TunnelHandler h = connectedDevices.get(addr.did);

        if (h == null && !strictMatch) {
            // pick a random DID for the given user
            Set<DID> dids = connectedDevices.keySet();
            h = connectedDevices.get(Iterables.get(dids, _random.nextInt(dids.size())));
        }

        return h;
    }
}

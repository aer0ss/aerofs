/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.havre.tunnel;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.Version;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.havre.EndpointConnector;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.aerofs.tunnel.ITunnelConnectionListener;
import com.aerofs.tunnel.TunnelAddress;
import com.aerofs.tunnel.TunnelHandler;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipeline;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.channels.ClosedChannelException;
import java.util.Map;
import java.util.Random;
import java.util.SortedSet;

import static com.google.common.base.Preconditions.checkState;

/**
 * Keeps track of tunnel connections from any number of {@link com.aerofs.tunnel.TunnelServer} and
 * offers an {@link EndpointConnector} interface to create virtual connections multiplexed on the
 * underlying physical tunnel connections.
 */
public class TunnelEndpointConnector implements ITunnelConnectionListener, EndpointConnector
{
    private final static Logger l = Loggers.getLogger(TunnelEndpointConnector.class);

    private final Random _random = new Random();
    private final EndpointVersionDetector _detector;
    private final Map<UserID, UserDevices> _endpointsByUser = Maps.newHashMap();

    private ITunnelConnectionListener _listener;

    private static class UserDevice implements Comparable<UserDevice>
    {
        public final DID did;
        public final Version version;
        public final TunnelHandler handler;

        UserDevice(DID did, Version version, TunnelHandler handler)
        {
            this.did = did;
            this.version = version;
            this.handler = handler;
        }

        @Override
        public boolean equals(Object o)
        {
            return this == o || (o != null && o instanceof UserDevice
                                         && version.equals(((UserDevice)o).version)
                                         && did.equals(((UserDevice)o).did));
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(version, did);
        }

        // sort by version first for easy filtering of the set of devices base on version bounds
        @Override
        public int compareTo(@Nonnull UserDevice o)
        {
            int c = version.compareTo(o.version);
            if (c == 0) c = did.compareTo(o.did);
            return c;
        }

        static UserDevice lowest(Version version)
        {
            return new UserDevice(new DID(DID.LOWEST), version, null);
        }
    }

    private class UserDevices
    {
        private final Map<DID, UserDevice> _byDID = Maps.newHashMap();
        private final SortedSet<UserDevice> _byVersion = Sets.newTreeSet();

        void put_(DID did, TunnelHandler handler, Version version)
        {
            UserDevice d = new UserDevice(did, version, handler);

            UserDevice prev = _byDID.put(did, d);
            if (prev != null) _byVersion.remove(prev);
            checkState(_byVersion.add(d));
        }

        TunnelHandler get_(DID did, @Nullable Version minVersion)
        {
            UserDevice d = _byDID.get(did);
            return d != null && (minVersion == null || minVersion.compareTo(d.version) <= 0)
                    ? d.handler : null;
        }

        void remove_(DID did, TunnelHandler handler)
        {
            UserDevice d = _byDID.get(did);
            if (d != null && d.handler == handler) {
                _byDID.remove(did);
                checkState(_byVersion.remove(d));
            }
        }

        boolean isEmpty_()
        {
            return _byDID.isEmpty();
        }

        /**
         * Pick a random device that support the requested minimum version
         */
        @Nullable TunnelHandler pick_(@Nullable Version minVersion)
        {
            // filter out endpoints with version strictly lower than minVersion
            SortedSet<UserDevice> candidates = minVersion == null
                    ? _byVersion : _byVersion.tailSet(UserDevice.lowest(minVersion));

            l.debug("v: {} cand: {}", minVersion, candidates.size());
            return candidates.isEmpty() ? null
                    : Iterables.get(candidates, _random.nextInt(candidates.size())).handler;
        }
    }

    public TunnelEndpointConnector(EndpointVersionDetector detector)
    {
        _detector = detector;
    }

    public void setListener(ITunnelConnectionListener listener)
    {
        _listener = listener;
    }

    @Override
    public synchronized @Nullable Channel connect(AuthenticatedPrincipal principal, DID did,
            boolean strictMatch, @Nullable Version minVersion, ChannelPipeline pipeline)
    {
        TunnelHandler tunnel = getEndpoint(principal, did, strictMatch, minVersion);
        return tunnel != null ? tunnel.newVirtualChannel(pipeline) : null;
    }

    @Override
    public DID device(Channel channel)
    {
        return ((TunnelAddress)channel.getRemoteAddress()).did;
    }

    @Override
    public synchronized void tunnelOpen(final TunnelAddress addr, final TunnelHandler handler)
    {
        final Channel c = handler.newVirtualChannel(_detector.getPipeline());
        Futures.addCallback(_detector.detectHighestSupportedVersion(c),
                new FutureCallback<Version>() {
                    @Override
                    public void onSuccess(Version version)
                    {
                        tunnelOpen(addr, handler, version);
                        c.close();
                    }

                    @Override
                    public void onFailure(Throwable throwable)
                    {
                        c.close();
                        l.warn("could not determine supported version ",
                                BaseLogUtil.suppress(throwable, ClosedChannelException.class));
                    }
                });
    }

    private synchronized void tunnelOpen(TunnelAddress addr, TunnelHandler handler, Version version)
    {
        Preconditions.checkNotNull(version);
        l.info("tunnel open {} {}", addr, version);
        UserDevices connectedDevices = _endpointsByUser.get(addr.user);
        if (connectedDevices == null) {
            connectedDevices = new UserDevices();
            _endpointsByUser.put(addr.user, connectedDevices);
        }
        connectedDevices.put_(addr.did, handler, version);
        if (_listener != null) _listener.tunnelOpen(addr, handler);
    }

    @Override
    public synchronized void tunnelClosed(TunnelAddress addr, TunnelHandler handler)
    {
        l.info("tunnel closed {} {}", addr, handler);
        UserDevices connectedDevices = _endpointsByUser.get(addr.user);
        if (connectedDevices != null) {
            connectedDevices.remove_(addr.did, handler);
            if (_listener != null) _listener.tunnelClosed(addr, handler);
        }
    }

    private @Nullable TunnelHandler getEndpoint(AuthenticatedPrincipal principal, DID did,
            boolean strictMatch, @Nullable Version minVersion)
    {
        // prefer talking to Team Server
        TunnelHandler handler = getEndpoint(teamServer(principal), did, strictMatch, minVersion);
        return handler != null
                ? handler
                : getEndpoint(principal.getEffectiveUserID(), did, strictMatch, minVersion);
    }

    private static UserID teamServer(AuthenticatedPrincipal principal)
    {
        return principal.getOrganizationID().toTeamServerUserID();
    }

    private @Nullable TunnelHandler getEndpoint(UserID user, DID did, boolean strictMatch,
            @Nullable Version minVersion)
    {
        l.debug("get {} {} {}", user, did, minVersion);
        TunnelAddress addr = new TunnelAddress(user, did);
        UserDevices connectedDevices = _endpointsByUser.get(addr.user);
        if (connectedDevices == null || connectedDevices.isEmpty_()) return null;

        TunnelHandler h = connectedDevices.get_(addr.did, minVersion);
        return strictMatch || h != null ? h : connectedDevices.pick_(minVersion);
    }
}

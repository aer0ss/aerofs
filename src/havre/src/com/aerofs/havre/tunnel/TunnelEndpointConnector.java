/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.havre.tunnel;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.Loggers;
import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import com.aerofs.havre.EndpointConnector;
import com.aerofs.havre.TeamServerInfo;
import com.aerofs.havre.Version;
import com.aerofs.oauth.AuthenticatedPrincipal;
import com.aerofs.tunnel.ITunnelConnectionListener;
import com.aerofs.tunnel.TunnelAddress;
import com.aerofs.tunnel.TunnelHandler;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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
        public final Set<UserID> usersInShard;
        public final TunnelHandler handler;

        UserDevice(DID did, Version version, TunnelHandler handler)
        {
            this.did = did;
            this.version = version;
            this.handler = handler;
            this.usersInShard = version instanceof TeamServerInfo
                    ? Sets.newHashSet(((TeamServerInfo)version).users)
                    : Collections.emptySet();
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

        /**
         * Only relevant for Team Servers
         *
         * An empty list of users implies that the TS is NOT sharded and can serve the entire org
         *
         * @return true if the team Server can service requests for the given user
         */
        public boolean canServiceUser(UserID user)
        {
            return usersInShard.isEmpty() || usersInShard.contains(user);
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

        UserDevice get_(DID did, @Nullable Version minVersion)
        {
            UserDevice d = _byDID.get(did);
            return d != null && (minVersion == null || minVersion.compareTo(d.version) <= 0)
                    ? d : null;
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

        SortedSet<UserDevice> suitableDevices(@Nullable Version minVersion)
        {
            return minVersion == null
                    ? _byVersion : _byVersion.tailSet(UserDevice.lowest(minVersion));
        }

        SortedSet<UserDevice> suitableDevices(@Nullable Version minVersion, UserID user)
        {
            return Sets.filter(suitableDevices(minVersion), ud -> ud.canServiceUser(user));
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

    private static class EndpointConstraint
    {
        private final AuthenticatedPrincipal principal;
        private final @Nullable Version minVersion;

        private EndpointConstraint(AuthenticatedPrincipal p, Version v)
        {
            principal = p;
            minVersion = v;
        }
    }

    @Override
    public synchronized @Nullable Channel connect(AuthenticatedPrincipal principal, DID did,
            boolean strictMatch, @Nullable Version minVersion, ChannelPipeline pipeline)
    {
        TunnelHandler tunnel = getEndpoint(principal, did, strictMatch, minVersion);
        Channel channel = tunnel != null ? tunnel.newVirtualChannel(pipeline) : null;
        if (channel != null && !strictMatch) {
            channel.setAttachment(new EndpointConstraint(principal, minVersion));
        }
        return channel;
    }

    @Override
    public DID device(Channel channel)
    {
        return ((TunnelAddress)channel.getRemoteAddress()).did;
    }

    @Override
    public Iterable<DID> alternateDevices(Channel channel)
    {
        Object o = channel.getAttachment();
        if (o == null || !(o instanceof EndpointConstraint)) return Collections.emptyList();
        EndpointConstraint c = (EndpointConstraint)o;

        final DID did = device(channel);
        return Iterables.transform(
                Iterables.filter(
                        getSuitableDevices(c.principal, c.minVersion),
                        ud -> !did.equals(ud.did)),
                ud -> ud.did);
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
        l.info("get {} {} {}", principal.getEffectiveUserID(), did, minVersion);

        TunnelHandler h = getMatchingEndpoint(principal, did, minVersion);
        if (strictMatch || h != null) return h;

        List<UserDevice> candidates = getSuitableDevices(principal, minVersion);
        l.info("v: {} cand: {}", minVersion, candidates.size());

        return candidates.isEmpty() ? null
                : candidates.get(_random.nextInt(candidates.size())).handler;
    }

    private TunnelHandler getMatchingEndpoint(AuthenticatedPrincipal principal, DID did,
            @Nullable Version minVersion)
    {
        UserDevice ud = getMatchingEndpoint(principal.getEffectiveUserID(), did, minVersion);
        if (ud != null) return ud.handler;
        ud = getMatchingEndpoint(teamServer(principal), did, minVersion);
        if (ud != null && ud.canServiceUser(principal.getEffectiveUserID())) return ud.handler;
        return null;
    }

    private UserDevice getMatchingEndpoint(UserID user, DID did, @Nullable Version minVersion)
    {
        UserDevices connected = _endpointsByUser.get(user);
        return connected != null ? connected.get_(did, minVersion) : null;
    }

    private static UserID teamServer(AuthenticatedPrincipal principal)
    {
        return principal.getOrganizationID().toTeamServerUserID();
    }

    private List<UserDevice> getSuitableDevices(AuthenticatedPrincipal principal,
            @Nullable Version minVersion)
    {
        List<UserDevice> l = Lists.newArrayList();

        UserDevices ts = _endpointsByUser.get(teamServer(principal));
        if (ts != null && !ts.isEmpty_()) {
            l.addAll(ts.suitableDevices(minVersion, principal.getEffectiveUserID()));
        }

        UserDevices user = _endpointsByUser.get(principal.getEffectiveUserID());
        if (user != null && !user.isEmpty_()) {
            l.addAll(user.suitableDevices(minVersion));
        }

        return l;
    }
}

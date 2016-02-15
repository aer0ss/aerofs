/**
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.transport.tcp;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.transport.lib.PresenceLocations.DeviceLocations;
import com.aerofs.daemon.transport.lib.exceptions.ExTransportUnavailable;
import com.aerofs.daemon.transport.lib.*;
import com.aerofs.daemon.transport.lib.presence.IPresenceLocation;
import com.aerofs.daemon.transport.presence.LocationManager;
import com.aerofs.daemon.transport.presence.TCPPresenceLocation;
import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.event.lib.EventDispatcher;
import com.aerofs.daemon.link.ILinkStateListener;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.daemon.transport.lib.exceptions.ExDeviceUnavailable;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler.ChannelMode;
import com.aerofs.daemon.transport.lib.handlers.TransportProtocolHandler;
import com.aerofs.lib.ClientParam;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.sched.Scheduler;
import com.aerofs.proto.Diagnostics.*;
import com.aerofs.proto.Transport;
import com.aerofs.proto.Transport.PBTPHeader;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Message;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map.Entry;

import static com.aerofs.daemon.transport.lib.TransportProtocolUtil.setupCommonHandlersAndListeners;
import static com.aerofs.proto.Transport.PBStream.Type.BEGIN_STREAM;
import static com.aerofs.proto.Transport.PBTPHeader.Type.STREAM;
import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;

// FIXME (AG): remove direct call from TCPStores and make this final
public class TCP implements ITransport, ILinkStateListener
{
    private static final Logger l = Loggers.getLogger(TCP.class);

    private final TransportEventQueue transportEventQueue;
    private final EventDispatcher dispatcher;
    private final Scheduler scheduler;
    private final String id;
    private final int rank;
    private final TransportStats transportStats;
    private final Unicast unicast;
    private final StreamManager streamManager;
    public final ChannelMonitor monitor;
    private final LocationManager locationManager;
    private final PortRange portRange;

    public TCP(
            UserID localUser,
            DID localdid,
            long streamTimeout,
            String id,
            int rank,
            IBlockingPrioritizedEventSink<IEvent> outgoingEventSink,
            LinkStateService linkStateService,
            long channelConnectTimeout,
            long heartbeatInterval,
            int maxFailedHeartbeats,
            SSLEngineFactory clientSslEngineFactory,
            SSLEngineFactory serverSslEngineFactory,
            Timer timer,
            ClientSocketChannelFactory clientChannelFactory,
            ServerSocketChannelFactory serverChannelFactory,
            LocationManager locationManager,
            IRoundTripTimes roundTripTimes)
    {
        this.dispatcher = new EventDispatcher();
        this.transportEventQueue = new TransportEventQueue(id, this.dispatcher);
        this.scheduler = new Scheduler(this.transportEventQueue, id + "-sch");

        this.id = id;
        this.rank = rank;
        this.transportStats = new TransportStats();
        this.streamManager = new StreamManager(streamTimeout);
        this.portRange = PortRange.loadFromConfiguration();

        this.locationManager = locationManager;

        // unicast
        PresenceService presenceService = new PresenceService(this, outgoingEventSink);
        PresenceLocations locations = new PresenceLocations();
        this.unicast = new Unicast(did -> {
            // address resolver: pick first candidate location
            // it will be removed if it fails
            DeviceLocations locs = locations.get(did);
            IPresenceLocation loc = locs != null ? locs.candidate() : null;
            if (loc == null) throw new ExDeviceUnavailable(did.toString());
            return ((TCPPresenceLocation)loc).socketAddress();
        }, this);
        monitor = new ChannelMonitor(unicast, locations, unicast.getDirectory(), timer);

        ChannelTeardownHandler serverChannelTeardownHandler = new ChannelTeardownHandler(this, streamManager, ChannelMode.SERVER);
        ChannelTeardownHandler clientChannelTeardownHandler = new ChannelTeardownHandler(this, streamManager, ChannelMode.CLIENT);
        TransportProtocolHandler protocolHandler = new TransportProtocolHandler(this, outgoingEventSink, streamManager);

        TCPBootstrapFactory bootstrapFactory = new TCPBootstrapFactory(
                localUser,
                localdid,
                channelConnectTimeout,
                heartbeatInterval,
                maxFailedHeartbeats,
                clientSslEngineFactory,
                serverSslEngineFactory,
                presenceService,
                unicast,
                protocolHandler,
                transportStats,
                timer,
                roundTripTimes);
        ServerBootstrap serverBootstrap = bootstrapFactory.newServerBootstrap(serverChannelFactory,
                serverChannelTeardownHandler);
        ClientBootstrap clientBootstrap = bootstrapFactory.newClientBootstrap(clientChannelFactory, clientChannelTeardownHandler);
        unicast.setBootstraps(serverBootstrap, clientBootstrap);

        // presence hookups
        unicast.setUnicastStateListener(presenceService);
        unicast.setDeviceConnectionListener(presenceService);
        presenceService.addListener(monitor);

        linkStateService.addListener(this, sameThreadExecutor());
    }

    @Override
    public void onLinkStateChanged(ImmutableSet<NetworkInterface> previous,
                                   ImmutableSet<NetworkInterface> current,
                                   ImmutableSet<NetworkInterface> added,
                                   ImmutableSet<NetworkInterface> removed) {
        // For TCP only, the link state listeners have a strange (and bad) interdependency.
        // The Multicast class controls whether we are sending/receiving tcp ping/pong traffic, and
        // winds up talking to ARP.
        // The Unicast infrastructure does bad things if it is asked to deal with a peer before the
        // link state (suspended/resumed) flag has been updated internally.
        //
        // Since the Notifier as we use it does not provide a way to guarantee safe ordering, we
        // do so explicitly.
        unicast.onLinkStateChanged(previous, current, added, removed);

        // update advertised presence locations
        // FIXME: bind to interfaces individually and only advertise those to which we're bound
        if (!added.isEmpty() || !removed.isEmpty()) {
            int port = getListeningPort();
            List<IPresenceLocation> locations = new ArrayList<>();
            for (NetworkInterface iface: current) {
                for (Enumeration<InetAddress> e = iface.getInetAddresses(); e.hasMoreElements();) {
                    locations.add(new TCPPresenceLocation(removeScope(e.nextElement()), port));
                }
            }
            locationManager.onLocationChanged(this, locations);
        }
    }

    // IPv6 addresses are scoped to the network interface they are listed from
    // this scope is encoded into the string representation, which is problematic when advertising
    // the IP to remote peers...
    private InetAddress removeScope(InetAddress addr) {
        try {
            return InetAddress.getByAddress(addr.getAddress());
        } catch (Exception e) {
            return addr;
        }
    }

    @Override
    public void init() throws Exception
    {
        // must be called *after* the Unicast object is initialized.
        setupCommonHandlersAndListeners(dispatcher, streamManager, unicast);
    }

    @Override
    public void start()
    {
        // Start these in the right order; bind the listener, THEN start processing the queues.
        // Otherwise, handling events could die; some handlers assume the transport was started
        while (portRange.hasNext()) {
            try {
                unicast.start(new InetSocketAddress(portRange.next()));
                break;
            } catch (ChannelException e) {
                if (portRange.hasNext()) {
                    l.info("Failed to bind to local port, retrying on next port", e);
                } else {
                    throw e;
                }
            }
        }
        transportEventQueue.start();

        l.info("listening to {}", getListeningPort());
    }

    @Override
    public void stop()
    {
        unicast.stop();
        scheduler.shutdown();
        transportEventQueue.stop();
    }

    @Override
    public String id()
    {
        return id;
    }

    @Override
    public String toString()
    {
        return id();
    }

    @Override
    public int rank()
    {
        return rank;
    }

    @Override
    public IBlockingPrioritizedEventSink<IEvent> q()
    {
        return transportEventQueue;
    }

    @Override
    public OutgoingStream newOutgoingStream(DID did)
            throws ExDeviceUnavailable, ExTransportUnavailable
    {
        StreamKey sk = streamManager.newOutgoingStreamKey(did);

        PBTPHeader h = PBTPHeader.newBuilder()
                .setType(STREAM)
                .setStream(Transport.PBStream
                        .newBuilder()
                        .setType(BEGIN_STREAM)
                        .setStreamId(sk.strmid.getInt()))
                .build();

        // NB. we will not catch failures of sending ctrl msg. however it
        // will be reflected when sending the payload data below
        Channel channel = (Channel)unicast.send(did, TransportProtocolUtil.newControl(h), null);
        return streamManager.newOutgoingStream(sk, channel);
    }

    /**
     * @return the port that the tcp server is listening to
     */
    int getListeningPort()
    {
        return ((InetSocketAddress)unicast.getListeningAddress()).getPort();
    }

    @Override
    public void dumpDiagnostics(TransportDiagnostics.Builder transportDiagnostics)
    {
        transportDiagnostics.setTcpDiagnostics(getDiagnostics());
    }

    private TCPDiagnostics getDiagnostics()
    {
        final TCPDiagnostics.Builder diagnostics = TCPDiagnostics.newBuilder();

        // listening port

        int listeningPort = ((InetSocketAddress) unicast.getListeningAddress()).getPort();
        PBInetSocketAddress.Builder ourAddress = PBInetSocketAddress.newBuilder();
        if (listeningPort > 0) {
            ourAddress.setHost("*").setPort(listeningPort);
        }
        diagnostics.setListeningAddress(ourAddress);

        DID prev = null;
        TCPDevice.Builder bd = null;
        // reachable_devices
        for (Entry<DID, Channel> e : unicast.getDirectory().getAllEntries()) {
            DID did = e.getKey();
            if (prev == null || !did.equals(prev)) {
                if (bd != null) diagnostics.addReachableDevices(bd);
                bd = TCPDevice.newBuilder();
                bd.setDid(BaseUtil.toPB(did));
                prev = did;
            }

            for (Message message : unicast.getChannelDiagnostics(did)) {
                bd.addChannel((TCPChannel) message);
            }
        }
        if (bd != null) diagnostics.addReachableDevices(bd);

        return diagnostics.build();
    }

    @Override
    public long bytesIn()
    {
        return transportStats.getBytesReceived();
    }

    @Override
    public long bytesOut()
    {
        return transportStats.getBytesSent();
    }

    private static class PortRange
    {
        final int low, high;
        private int offset;

        PortRange(int low, int high)
        {
            this.low = low;
            this.high = high;
            this.offset = 0;
        }

        boolean hasNext()
        {
            return low + offset <= high;
        }

        int next()
        {
            return this.low + (offset++);
        }

        static PortRange loadFromConfiguration()
        {
            int low = ClientParam.Daemon.PORT_RANGE_LOW, high = ClientParam.Daemon.PORT_RANGE_HIGH;
            if (low > high) {
                l.warn("invalid range of ports for daemon to use: {}-{}, defaulting to any port", low, high);
                return new PortRange(0, 0);
            } else if (high > 0 && low <= 1024) {
                l.warn("invalid range of ports for daemon to use: {}-{} includes reserved ports, defaulting to any port", low, high);
                return new PortRange(0, 0);
            }
            return new PortRange(Math.max(0, low), Math.min(65535, high));
        }
    }
}

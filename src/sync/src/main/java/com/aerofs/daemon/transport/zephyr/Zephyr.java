/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.zephyr;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.transport.ISignallingService;
import com.aerofs.daemon.transport.lib.exceptions.ExDeviceUnavailable;
import com.aerofs.daemon.transport.lib.exceptions.ExTransportUnavailable;
import com.aerofs.daemon.transport.lib.*;
import com.aerofs.daemon.transport.lib.presence.IPresenceLocation;
import com.aerofs.daemon.transport.presence.ZephyrPresenceLocation;
import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.event.lib.EventDispatcher;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler;
import com.aerofs.daemon.transport.lib.handlers.TransportProtocolHandler;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.sched.Scheduler;
import com.aerofs.proto.Diagnostics.ServerStatus;
import com.aerofs.proto.Diagnostics.TransportDiagnostics;
import com.aerofs.proto.Diagnostics.ZephyrDevice;
import com.aerofs.proto.Diagnostics.ZephyrDiagnostics;
import com.aerofs.proto.Transport;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Collection;
import java.util.Set;

import static com.aerofs.daemon.transport.lib.TransportProtocolUtil.setupCommonHandlersAndListeners;
import static com.aerofs.daemon.transport.lib.TransportUtil.fromInetSockAddress;
import static com.aerofs.daemon.transport.lib.TransportUtil.getReachabilityErrorString;
import static com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler.ChannelMode.TWOWAY;
import static com.aerofs.proto.Transport.PBStream.Type.BEGIN_STREAM;
import static com.aerofs.proto.Transport.PBTPHeader.Type.STREAM;
import static com.google.common.base.Preconditions.checkState;

public final class Zephyr implements ITransport
{
    protected static final Logger l = Loggers.getLogger(Zephyr.class);

    private final String id;
    private final int rank; // FIXME (AG): why does the transport need to know its own preference
    private final DID localdid;

    private final TransportEventQueue transportEventQueue;
    private final EventDispatcher dispatcher;
    private final Scheduler scheduler;

    private final StreamManager streamManager;

    private final InetSocketAddress zephyrAddress;
    private final ZephyrConnectionService zephyrConnectionService;

    private final TransportStats transportStats = new TransportStats();
    public final ChannelMonitor monitor;
    public final ZephyrPresence presence;

    public Zephyr(
            UserID localid,
            DID localdid,
            long streamTimeout,
            String id,
            int rank,
            IBlockingPrioritizedEventSink<IEvent> outgoingEventSink,
            LinkStateService linkStateService,
            SSLEngineFactory clientSSLEngineFactory,
            SSLEngineFactory serverSSLEngineFactory,
            ClientSocketChannelFactory clientSocketChannelFactory,
            Timer timer,
            long heartbeatInterval,
            int maxFailedHeartbeats,
            long zephyrHandshakeTimeout,
            ZephyrParams zephyrParams,
            Proxy proxy,
            IRoundTripTimes roundTripTimes,
            ISignallingService signallingService)
    {
        checkState(DaemonParam.XMPP.CONNECT_TIMEOUT > DaemonParam.Zephyr.HANDSHAKE_TIMEOUT); // should be much larger!

        this.dispatcher = new EventDispatcher();
        this.transportEventQueue = new TransportEventQueue(id, this.dispatcher);
        this.scheduler = new Scheduler(this.transportEventQueue, id + "-sch");
        this.streamManager = new StreamManager(streamTimeout);

        this.id = id;
        this.rank = rank;
        this.localdid = localdid;

        PresenceService presenceService = new PresenceService();

        TransportProtocolHandler transportProtocolHandler = new TransportProtocolHandler(this, outgoingEventSink, streamManager);
        ChannelTeardownHandler channelTeardownHandler = new ChannelTeardownHandler(this, outgoingEventSink, streamManager, TWOWAY);
        this.zephyrAddress = zephyrParams.serverAddress;
        this.zephyrConnectionService = new ZephyrConnectionService(
                localid,
                localdid,
                heartbeatInterval,
                maxFailedHeartbeats,
                zephyrHandshakeTimeout,
                clientSSLEngineFactory,
                serverSSLEngineFactory,
                this,
                presenceService,
                presenceService,
                linkStateService,
                signallingService,
                transportProtocolHandler,
                channelTeardownHandler,
                transportStats,
                timer,
                clientSocketChannelFactory,
                this.zephyrAddress,
                proxy,
                roundTripTimes);

        this.monitor = new ChannelMonitor(zephyrConnectionService.getDirectory(), timer);
        this.presence = new ZephyrPresence(this, outgoingEventSink);

        presenceService.addListener(monitor);
        presenceService.addListener(presence);

        l.debug("{}: enabling multicast", id());
    }

    @Override
    public void init()
            throws Exception
    {
        setupCommonHandlersAndListeners(dispatcher, streamManager, zephyrConnectionService);
        zephyrConnectionService.init();
    }

    @Override
    public void start()
    {
        transportEventQueue.start();
        zephyrConnectionService.start();
    }

    @Override
    public void stop()
    {
        zephyrConnectionService.stop();
        scheduler.shutdown();
        transportEventQueue.stop();
    }

    @Override
    public String id()
    {
        return id;
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

        Transport.PBTPHeader h = Transport.PBTPHeader.newBuilder()
                .setType(STREAM)
                .setStream(Transport.PBStream
                        .newBuilder()
                        .setType(BEGIN_STREAM)
                        .setStreamId(sk.strmid.getInt()))
                .build();

        // NB. we will not catch failures of sending ctrl msg. however it
        // will be reflected when sending the payload data below
        Channel channel = (Channel)zephyrConnectionService.send(did, TransportProtocolUtil.newControl(h), null);
        return streamManager.newOutgoingStream(sk, channel);
    }

    /**
     * Return the Zephyr locations
     *
     * @return The list of presence locations
     */
    @Override
    public Collection<IPresenceLocation> getPresenceLocations() {
        // We have the Zephyr IP and port
        // And we only have one location (one Zephyr server)
        return ImmutableList.of(new ZephyrPresenceLocation(localdid, zephyrAddress));
    }

    @Override
    public String toString()
    {
        return id();
    }

    @Override
    public void dumpDiagnostics(TransportDiagnostics.Builder transportDiagnostics)
    {
        transportDiagnostics.setZephyrDiagnostics(getDiagnostics());
    }

    private ZephyrDiagnostics getDiagnostics()
    {
        ZephyrDiagnostics.Builder diagnostics = ZephyrDiagnostics.newBuilder();

        // zephyr

        ServerStatus.Builder zephyrServerStatus = ServerStatus
                .newBuilder()
                .setServerAddress(fromInetSockAddress(zephyrAddress));

        try {
            zephyrServerStatus.setReachable(zephyrConnectionService.isReachable());
        } catch (IOException e) {
            zephyrServerStatus.setReachable(false);
            zephyrServerStatus.setReachabilityError(getReachabilityErrorString(zephyrServerStatus, e));
        }

        diagnostics.setZephyrServer(zephyrServerStatus);

        // devices

        // get all devices available to the presence service
        Set<DID> availableDevices = Sets.newHashSet(monitor.allReachableDevices());

        // get all devices for which we have connections
        Collection<ZephyrDevice> connectedDevices = zephyrConnectionService.getDeviceDiagnostics();

        // remove all devices that we know for which we have connections
        for (ZephyrDevice device : connectedDevices) {
            availableDevices.remove(new DID(BaseUtil.fromPB(device.getDid())));
        }

        // add these 'empty' devices first
        for (DID did : availableDevices) {
            diagnostics.addReachableDevices(ZephyrDevice.newBuilder().setDid(BaseUtil.toPB(did)));
        }

        // now add all the devices for which we have connections
        diagnostics.addAllReachableDevices(connectedDevices);

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
}

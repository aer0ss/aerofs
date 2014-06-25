/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.transport.xray;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.daemon.link.ILinkStateListener;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.daemon.transport.ExDeviceUnavailable;
import com.aerofs.daemon.transport.ExTransport;
import com.aerofs.daemon.transport.ExTransportUnavailable;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.ChannelDirectory;
import com.aerofs.daemon.transport.lib.IUnicastConnector;
import com.aerofs.daemon.transport.lib.IUnicastInternal;
import com.aerofs.daemon.transport.lib.IUnicastListener;
import com.aerofs.daemon.transport.lib.TransportStats;
import com.aerofs.daemon.transport.lib.TransportUtil;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler;
import com.aerofs.daemon.transport.lib.handlers.TransportProtocolHandler;
import com.aerofs.daemon.transport.xmpp.signalling.ISignallingService;
import com.aerofs.daemon.transport.xmpp.signalling.ISignallingServiceListener;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.proto.Diagnostics.ChannelState;
import com.aerofs.proto.Diagnostics.XRayChannel;
import com.aerofs.proto.Diagnostics.XRayDevice;
import com.aerofs.rocklog.RockLog;
import com.aerofs.xray.client.IZephyrSignallingService;
import com.aerofs.xray.client.exceptions.ExHandshakeFailed;
import com.aerofs.xray.client.exceptions.ExHandshakeRenegotiation;
import com.aerofs.xray.proto.XRay.ZephyrControlMessage;
import com.aerofs.xray.proto.XRay.ZephyrHandshake;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.protobuf.InvalidProtocolBufferException;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Proxy;
import java.net.Socket;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.aerofs.base.net.ZephyrConstants.ZEPHYR_REG_MSG_LEN;
import static com.aerofs.daemon.transport.lib.TransportDefects.DEFECT_NAME_HANDSHAKE_RENEGOTIATION;
import static com.aerofs.daemon.transport.lib.TransportUtil.newConnectedSocket;
import static com.aerofs.daemon.transport.xray.XRayClientPipelineFactory.getZephyrClient;
import static com.aerofs.xray.proto.XRay.ZephyrControlMessage.Type.HANDSHAKE;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Sets.newHashSet;
import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;

/**
 * Creates and manages connections to a Zephyr relay server
 */
final class XRayConnectionService implements ILinkStateListener, IUnicastInternal, IZephyrSignallingService, ISignallingServiceListener, IUnicastConnector
{
    private static final Predicate<Channel> TRUE_FILTER = new Predicate<Channel>()
    {
        @Override
        public boolean apply(@Nullable Channel channel)
        {
            return true;
        }
    };

    private static Logger l = Loggers.getLogger(XRayConnectionService.class);

    private final RockLog rockLog;
    private final LinkStateService linkStateService;
    private final ISignallingService signallingService;
    private final IUnicastListener unicastListener;
    private final ChannelDirectory directory;

    private final InetSocketAddress xrayAddress;
    private final ClientBootstrap bootstrap;

    private final AtomicBoolean running = new AtomicBoolean(false);

    XRayConnectionService(
            UserID localid,
            DID localdid,
            long hearbeatInterval,
            int maxFailedHeartbeats,
            long xrayHandshakeTimeout,
            SSLEngineFactory clientSslEngineFactory,
            SSLEngineFactory serverSslEngineFactory,
            ITransport transport,
            IUnicastListener unicastListener,
            LinkStateService linkStateService,
            ISignallingService signallingService,
            TransportProtocolHandler transportProtocolHandler,
            ChannelTeardownHandler channelTeardownHandler,
            TransportStats transportStats,
            Timer timer,
            RockLog rockLog,
            ChannelFactory channelFactory,
            InetSocketAddress xrayAddress,
            Proxy proxy)
    {
        this.xrayAddress = xrayAddress;
        this.bootstrap = new ClientBootstrap(channelFactory);
        this.bootstrap.setPipelineFactory(
                new XRayClientPipelineFactory(
                        localid,
                        localdid,
                        clientSslEngineFactory,
                        serverSslEngineFactory,
                        transportProtocolHandler,
                        channelTeardownHandler,
                        transportStats,
                        this,
                        unicastListener,
                        timer,
                        rockLog,
                        proxy,
                        hearbeatInterval,
                        maxFailedHeartbeats,
                        xrayHandshakeTimeout));

        this.rockLog = rockLog;

        this.linkStateService = linkStateService;
        this.signallingService = signallingService;
        this.unicastListener = unicastListener;
        this.directory = new ChannelDirectory(transport, this);
        directory.setUnicastListener(unicastListener);
    }

    //
    // startup/shutdown
    //

    void init()
    {
        signallingService.registerSignallingClient(this);
        linkStateService.addListener(this, sameThreadExecutor()); // our callback implementation is thread-safe and can be called on the notifier thread
    }

    void start()
    {
        boolean alreadyRunning = running.getAndSet(true);
        if (alreadyRunning) return;

        l.info("start");
    }

    void stop()
    {
        boolean alreadyRunning = running.getAndSet(false);
        if (!alreadyRunning) return;

        l.info("stop");

        synchronized (this) {
            disconnectChannels(TRUE_FILTER, new ExTransportUnavailable("connection service stopped"));
        }
    }

    @Override
    public void onLinkStateChanged(
            ImmutableSet<NetworkInterface> previous,
            ImmutableSet<NetworkInterface> current,
            ImmutableSet<NetworkInterface> added,
            ImmutableSet<NetworkInterface> removed)
    {
        // FIXME (AG): this is not foolproof
        // Notably, if one has a ton of virtual devices that do not ever
        // go down, then we will never switch from 'ready' -> 'unavailable'
        if (previous.isEmpty() && !current.isEmpty()) {
            unicastListener.onUnicastReady();
        } else if (!previous.isEmpty() && current.isEmpty()) {
            unicastListener.onUnicastUnavailable();
        }

        Predicate<Channel> channelsToDisconnectFilter;

        if (!current.isEmpty()) {
            final Set<InetAddress> removedAddresses = newHashSet();

            for (NetworkInterface networkInterface : removed) {
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    removedAddresses.add(addresses.nextElement());
                }
            }

            l.debug("removed addresses:{}", removedAddresses);

            // only remove the channels with a local address
            // that matches one belonging to the removed interface
            channelsToDisconnectFilter = new Predicate<Channel>()
            {
                @Override
                public boolean apply(@Nullable Channel entry)
                {
                    InetAddress address = ((InetSocketAddress) checkNotNull(entry).getLocalAddress()).getAddress();
                    l.trace("local address:{}", address);
                    return removedAddresses.contains(address);
                }
            };

        } else {
            // all interfaces went down
            // remove all the channels
            channelsToDisconnectFilter = TRUE_FILTER;
        }

        synchronized (this) {
            disconnectChannels(channelsToDisconnectFilter, new ExTransportUnavailable("link down"));
        }
    }

    @Override
    public ChannelFuture newChannel(DID did)
            throws ExTransportUnavailable, ExDeviceUnavailable
    {
        if (!running.get()) {
            l.warn("{} ignore connect - connection service stopped", did);
            return null;
        }
        l.trace("{} create channel", did);

        // start by creating the channel (NOTE: this is _binding_ only, not connecting)
        // this allows us to add state before starting the connection and lifecycle process
        Channel channel = bootstrap.bind(new InetSocketAddress("0.0.0.0", 0)).getChannel();

        // set up the ZephyrClientHandler
        getZephyrClient(channel).init(did, channel);

        // now, connect
        ChannelFuture connectFuture = channel.connect(xrayAddress);

        l.trace("{} connecting on created channel", did);
        return connectFuture;
    }

    @Override
    public void disconnect(DID did, Exception cause)
    {
        l.info("{} disconnect cause:{}", did, cause.getMessage());
        for (Channel c : directory.detach(did)) {
            getZephyrClient(c).disconnect(cause);
        }
    }

    private void disconnectChannels(Predicate<Channel> filter, Exception cause)
    {
        for (Channel channel : directory.getAllChannels()) {
            if (filter.apply(channel)) {
                getZephyrClient(channel).disconnect(cause);
            }
        }
    }

    @Override
    public Object send(final DID did, @Nullable final IResultWaiter wtr, Prio pri, byte[][] bss, @Nullable Object cke)
    {
        l.trace("{} send cke:{}", did, cke);

        Channel channel;
        try {
            synchronized (this) {
                // DirectoryChannel does not require outside synchronization. However, it
                // allows for more than one channel being created (if multiple threads arrive at
                // the zero-available state simultaneously). Zephyr does not want more than one
                // channel instance per DID, so we give the channel directory a stronger guarantee.
                // Lock ordering is important: always lock this first. ChannelDirectory will not
                // reach back to the Unicast infrastructure with the ChannelDirectory monitor held.
                channel = directory.chooseActiveChannel(did).getChannel();
            }
        } catch (ExTransport ex) {
            l.warn("{} x: channel failed", did, LogUtil.suppress(ex));
            return null;
        }

        if (channel == null) { // can happen because the upper layer hasn't yet been notified of a disconnection
            l.warn("{} no channel", did);
            if (wtr != null) {
                wtr.error(new ExDeviceUnavailable("no connection"));
            }
            return null;
        }

        if (cke != null && (cke instanceof Channel) && !cke.equals(channel)) { // this is a stream using an older channel
            l.warn("{} fail send - stale channel", did);
            if (wtr != null) {
                wtr.error(new ExDeviceUnavailable("stale connection"));
            }
            return null;
        }

        ChannelFuture writeFuture = channel.write(bss);
        writeFuture.addListener(new ChannelFutureListener()
        {
            @Override
            public void operationComplete(ChannelFuture future)
                    throws Exception
            {
                if (future.isSuccess()) {
                    if (wtr != null) wtr.okay();
                } else {
                    if (wtr != null) wtr.error(TransportUtil.newExTransportOrFatalOnError("fail send packet to " + did, future.getCause()));
                }
            }
        });

        return channel; // this is the channel used to send the packets
    }

    //
    // ISignallingServiceListener methods
    //

    @Override
    public void signallingServiceConnected()
    {
        // noop
    }

    @Override
    public void signallingServiceDisconnected()
    {
        // close anyone who hasn't completed their signalling. If your signalling
        // hasn't completed, then you're going to be sitting around waiting for
        // timeouts. Instead, why not just close you right now?

        Predicate<Channel> handshakeIncompletePredicate = new Predicate<Channel>()
        {
            @Override
            public boolean apply(@Nullable Channel channel)
            {
                XRayClientHandler zephyrClient = getZephyrClient(checkNotNull(channel));
                return (!zephyrClient.hasHandshakeCompleted()); // keep alive if you've completed handshaking
            }
        };

        synchronized (this) {
            disconnectChannels(handshakeIncompletePredicate, new ExTransportUnavailable("signalling service disconnected"));
        }
    }

    @Override
    public void processIncomingSignallingMessage(DID did, byte[] message)
    {
        if (!running.get()) {
            l.warn("{} <-sig drop - service not running");
            return;
        }

        final ZephyrHandshake handshake;

        try {
            ZephyrControlMessage control = ZephyrControlMessage.parseFrom(message);

            checkArgument(control.getType() == HANDSHAKE, "recv signalling msg with unexpected type exp:HANDSHAKE act:{}", control.getType());
            checkArgument(control.hasHandshake());

            handshake = control.getHandshake();
        } catch (InvalidProtocolBufferException e) {
            l.warn("{} recv invalid signalling message");
            return;
        }

        l.debug("{} <-sig ms:{} m{}", did, handshake.getSourceZephyrId(), handshake.getDestinationZephyrId());

        // FIXME (AG): I'm not a fan of this entire block because of 1) notification ordering and 2) trying to be too smart
        //
        // 1) Since notifications are sent via the netty I/O thread, it's possible to get the following order: disconnected, connected
        // 2) I'm not comfortable automatically initiating a connection on your behalf

        synchronized (this) {
            try {
                try {
                    consumeHandshake(did, handshake);
                } catch (ExHandshakeRenegotiation e) {
                    // the zephyr server breaks both legs of a connection
                    // if it detects that one leg has died
                    // there are cases where the remote peer knows
                    // that a connection is broken before the server does
                    // this may cause the remote peer to attempt to re-establish
                    // a connection. we want to detect this case an
                    // 1. immediately teardown the old connection with zephyr
                    // 2. immediately start negotiating the next connection
                    // doing this allows us to avoid an expensive handshake timeout
                    // on the remote peer and multiple additional roundtrips
                    rockLog.newDefect(DEFECT_NAME_HANDSHAKE_RENEGOTIATION).send();
                    disconnect(did, new IllegalStateException("attempted renegotiation of zephyr channel to " + did, e));
                    consumeHandshake(did, handshake);
                }
            } catch (Exception e) {
                disconnect(did, new ExDeviceUnavailable("fail to process signalling message from " + did, e));
            }
        }
    }

    private void consumeHandshake(DID did, ZephyrHandshake handshake)
            throws ExDeviceUnavailable, ExHandshakeFailed, ExHandshakeRenegotiation
    {
        // synchronization is not required by ChannelDirectory except that it allows multiple
        // peer channels for one Device. synchronization here guards the zero-to-one transition.
        Preconditions.checkArgument(Thread.holdsLock(this), "improper synchronization");

        try {
            Channel channel = directory.chooseActiveChannel(did).getChannel();
            getZephyrClient(channel).consumeHandshake(handshake);
        } catch (ExTransportUnavailable exTransportUnavailable) {
            throw new ExDeviceUnavailable("cannot reach " + did);
        }
    }

    @Override
    public void sendSignallingMessageFailed(DID did, byte[] failedmsg, Exception cause)
    {
        l.warn("{} ->sig fail err:{}", did, cause.getMessage());
        disconnect(did, new ExDeviceUnavailable("failed to send zephyr handshake to " + did, cause));
    }

    //
    // IZephyrSignallingService methods
    //

    @Override
    public void sendZephyrSignallingMessage(Channel sender, byte[] bytes)
    {
        DID did = getZephyrClient(sender).getExpectedRemoteDID();

        // outgoing signalling messages are only sent when we're still in connecting.
        // as a result, the upper layer won't be told of this disconnection

        if (!running.get()) {
            l.warn("{} ->sig ignored - connection service stopped", did);
            disconnect(did, new ExDeviceUnavailable("connection service stopped"));
            return;
        }

        l.debug("{} ->sig", did);

        signallingService.sendSignallingMessage(did, bytes, this);
    }

    //
    // debugging/printing
    //

    public synchronized Collection<XRayDevice> getDeviceDiagnostics()
    {
        Set<Map.Entry<DID, Channel>> entries = directory.getAllEntries();
        Collection<XRayDevice> devices = Lists.newArrayListWithExpectedSize(entries.size());

        for (Map.Entry<DID, Channel> entry : entries) {
            DID did = entry.getKey();
            Channel channel = entry.getValue();

            XRayClientHandler client = getZephyrClient(channel);
            XRayDevice device = XRayDevice
                    .newBuilder()
                    .setDid(did.toPB())
                    .addChannel(XRayChannel
                            .newBuilder()
                            .setState(getChannelState(client))
                            .setZidLocal(client.getLocalZid())
                            .setZidRemote(client.getRemoteZid())
                            .setBytesSent(client.getBytesSent())
                            .setBytesReceived(client.getBytesReceived())
                            .setLifetime(client.getChannelLifetime()))
                    .build();

            devices.add(device);
        }

        return devices;
    }

    private static ChannelState getChannelState(XRayClientHandler client)
    {
        if (client.isClosed()) {
            return ChannelState.CLOSED;
        } else if (client.hasHandshakeCompleted()) {
            return ChannelState.VERIFIED;
        } else {
            return ChannelState.CONNECTING;
        }
    }

    public boolean isReachable()
            throws IOException
    {
        Socket s = null;
        try {
            s = newConnectedSocket(xrayAddress, (int)(2 * C.SEC));
            InputStream zidInputStream = s.getInputStream();
            int bytes = zidInputStream.read(new byte[ZEPHYR_REG_MSG_LEN]);
            return bytes == ZEPHYR_REG_MSG_LEN;
        } catch (IOException e) {
            l.warn("fail xray reachability check", e);
            throw e;
        } finally {
            if (s != null) try {
                s.close();
            } catch (IOException e) {
                l.warn("fail close reachability socket with err:{}", e.getMessage());
            }
        }
    }

    public ChannelDirectory getDirectory() { return directory; }
}

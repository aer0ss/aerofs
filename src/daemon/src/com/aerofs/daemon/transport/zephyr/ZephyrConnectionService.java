package com.aerofs.daemon.transport.zephyr;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.daemon.link.ILinkStateListener;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.daemon.transport.ExDeviceUnavailable;
import com.aerofs.daemon.transport.ExIOFailed;
import com.aerofs.daemon.transport.ExTransportUnavailable;
import com.aerofs.daemon.transport.lib.IUnicastInternal;
import com.aerofs.daemon.transport.lib.IUnicastListener;
import com.aerofs.daemon.transport.lib.TransportStats;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler;
import com.aerofs.daemon.transport.lib.handlers.TransportProtocolHandler;
import com.aerofs.daemon.transport.xmpp.signalling.ISignallingService;
import com.aerofs.daemon.transport.xmpp.signalling.ISignallingServiceListener;
import com.aerofs.lib.event.Prio;
import com.aerofs.proto.Diagnostics.ChannelState;
import com.aerofs.proto.Diagnostics.ZephyrChannel;
import com.aerofs.proto.Diagnostics.ZephyrDevice;
import com.aerofs.rocklog.RockLog;
import com.aerofs.zephyr.client.IZephyrSignallingService;
import com.aerofs.zephyr.client.exceptions.ExHandshakeFailed;
import com.aerofs.zephyr.client.exceptions.ExHandshakeRenegotiation;
import com.aerofs.zephyr.proto.Zephyr.ZephyrControlMessage;
import com.aerofs.zephyr.proto.Zephyr.ZephyrHandshake;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.protobuf.InvalidProtocolBufferException;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
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
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.aerofs.base.net.ZephyrConstants.ZEPHYR_REG_MSG_LEN;
import static com.aerofs.daemon.lib.DaemonParam.Zephyr.HANDSHAKE_TIMEOUT;
import static com.aerofs.daemon.lib.DaemonParam.Zephyr.HEARTBEAT_INTERVAL;
import static com.aerofs.daemon.lib.DaemonParam.Zephyr.MAX_FAILED_HEARTBEATS;
import static com.aerofs.daemon.transport.lib.TransportDefects.DEFECT_NAME_HANDSHAKE_RENEGOTIATION;
import static com.aerofs.daemon.transport.lib.TransportUtil.newConnectedSocket;
import static com.aerofs.daemon.transport.zephyr.ZephyrClientPipelineFactory.getZephyrClient;
import static com.aerofs.zephyr.proto.Zephyr.ZephyrControlMessage.Type.HANDSHAKE;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.collect.Sets.newHashSet;
import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;

/**
 * Creates and manages connections to a Zephyr relay server
 */
final class ZephyrConnectionService implements ILinkStateListener, IUnicastInternal, IZephyrSignallingService, ISignallingServiceListener
{
    private static final Predicate<Entry<DID,Channel>> TRUE_FILTER = new Predicate<Entry<DID, Channel>>()
    {
        @Override
        public boolean apply(@Nullable Entry<DID, Channel> entry)
        {
            return true;
        }
    };

    private static Logger l = Loggers.getLogger(ZephyrConnectionService.class);

    private final RockLog rockLog;
    private final LinkStateService linkStateService;
    private final ISignallingService signallingService;
    private final IUnicastListener unicastListener;

    private final InetSocketAddress zephyrAddress;
    private final ClientBootstrap bootstrap;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private final ConcurrentMap<DID, Channel> channels = Maps.newConcurrentMap();

    ZephyrConnectionService(
            UserID localid,
            DID localdid,
            SSLEngineFactory clientSslEngineFactory,
            SSLEngineFactory serverSslEngineFactory,
            IUnicastListener unicastListener,
            LinkStateService linkStateService,
            ISignallingService signallingService,
            TransportProtocolHandler transportProtocolHandler,
            ChannelTeardownHandler channelTeardownHandler,
            TransportStats transportStats,
            RockLog rockLog,
            ChannelFactory channelFactory,
            InetSocketAddress zephyrAddress,
            Proxy proxy)
    {
        this.zephyrAddress = zephyrAddress;
        this.bootstrap = new ClientBootstrap(channelFactory);
        this.bootstrap.setPipelineFactory(
                new ZephyrClientPipelineFactory(
                        localid,
                        localdid,
                        clientSslEngineFactory,
                        serverSslEngineFactory,
                        transportProtocolHandler,
                        channelTeardownHandler,
                        transportStats,
                        this,
                        unicastListener,
                        proxy,
                        HANDSHAKE_TIMEOUT,
                        HEARTBEAT_INTERVAL,
                        MAX_FAILED_HEARTBEATS));

        this.rockLog = rockLog;

        this.linkStateService = linkStateService;
        this.signallingService = signallingService;
        this.unicastListener = unicastListener;
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

        final Set<InetAddress> removedAddresses = newHashSet();
        for (NetworkInterface networkInterface : removed) {
            Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                removedAddresses.add(addresses.nextElement());
            }
        }

        l.trace("removed addresses:{}", removedAddresses);

        Predicate<Entry<DID, Channel>> allChannelsFilter = new Predicate<Entry<DID, Channel>>()
        {
            @Override
            public boolean apply(@Nullable Entry<DID, Channel> entry)
            {
                InetAddress address = ((InetSocketAddress) checkNotNull(entry).getValue().getLocalAddress()).getAddress();
                l.info("local address:{}", address);
                return removedAddresses.contains(address);
            }
        };

        synchronized (this) {
            disconnectChannels(allChannelsFilter, new ExTransportUnavailable("link down"));
        }
    }

    private void connect(DID did)
    {
        if (!running.get()) {
            l.warn("{} ignore connect - connection service stopped", did);
            return;
        }

        try {
            l.info("{} connect", did);

            Channel channel = getChannel(did);
            if (channel != null) {
                if (getZephyrClient(channel).hasHandshakeCompleted()) {
                    // apparently the channel connected, but for some reason the
                    // upper layer doesn't think otherwise; this means that the channel
                    // isn't in a good state
                    disconnectChannel(did, new ExDeviceUnavailable("triggered by local reconnect"));
                } else {
                    // we're still in the process of handshaking - let's give it a
                    // chance. at any rate, if we fail to complete in time we'll get a
                    // timeout and it'll all be good
                    l.warn("{} ignore connect - handshake in progress", did);
                    return;
                }
            }

            newChannel(did);
        } catch (Exception e) {
            l.error("{} fail connect err:", did, e);
            if (getChannel(did) != null) {
                disconnectChannel(did, e);
            }
        }
    }

    private void newChannel(final DID did)
    {
        if (channels.containsKey(did)) { // can happen if the close future hasn't run yet
            l.warn("{} remove old channel for connect", did);
            removeChannel(did);
        }

        l.trace("{} create channel", did);

        // start by creating the channel (NOTE: this is _binding_ only, not connecting)
        // this allows us to add state before starting the connection and lifecycle process
        Channel channel = bootstrap.bind(new InetSocketAddress("0.0.0.0", 0)).getChannel();

        // set up the ZephyrClientHandler
        getZephyrClient(channel).init(did, channel);

        // now add the channel (IMPORTANT: do the add before setting up the close future)
        // we do this because we don't want to fail in an operation and then add the failed
        // channel to the map
        // FIXME (AG): I'm not sure the logic here is correct
        addChannel(did, channel);

        // now, connect
        channel.connect(zephyrAddress);

        // and, finally, set up the close future
        channel.getCloseFuture().addListener(new ChannelFutureListener()
        {
            @Override
            public void operationComplete(ChannelFuture future)
                    throws Exception
            {
                l.debug("{} close future triggered", did);

                // IMPORTANT: I remove the channel _without_ holding a lock
                // on ZephyrConnectionService to prevent a deadlock.
                //
                // All operations in ZephyrConnectionService hold a lock
                // on 'this' before modifying state. When close() is called on
                // a channel, it is generally an _IO thread_ that handles the
                // close and trips the close future. This can trigger a
                // deadlock if the close future attempts to grab a lock on
                // 'this' as well.
                //
                // Our close future simply has to remove a reference to the
                // closed channel, so we can get away with doing an unprotected (key, value)
                // remove

                Channel closedChannel = future.getChannel();
                boolean removed = channels.remove(did, closedChannel);
                if (removed) {
                    l.info("{} remove channel", did);
                } else {
                    l.warn("{} already removed channel", did);
                }
            }
        });

        l.trace("{} connecting on created channel", did);
    }

    @Override
    public synchronized void disconnect(DID did, Exception cause)
    {
        l.info("{} disconnect cause:{}", did, cause.getMessage());
        disconnectChannel(did, cause);
    }

    private void disconnectChannels(Predicate<Map.Entry<DID, Channel>> filter, Exception cause)
    {
        Map<DID, Channel> channelsCopy = newHashMap(channels); // copy to prevent ConcurrentModificationException
        for (Map.Entry<DID, Channel> entry : channelsCopy.entrySet()) {
            if (filter.apply(entry)) {
                disconnectChannel(entry.getKey(), cause);
            }
        }
    }

    private void disconnectChannel(DID did, Exception cause)
    {
        Channel channel = getChannel(did);
        if (channel == null) {
            l.warn("{} disconnect ignored - no channel", did);
            return;
        }

        getZephyrClient(channel).disconnect(cause);
    }

    @Override
    public Object send(final DID did, @Nullable final IResultWaiter wtr, Prio pri, byte[][] bss, @Nullable Object cke)
    {
        l.trace("{} send cke:{}", did, cke);

        Channel channel;
        synchronized (this) { // FIXME (AG): move all of this logic into connect
            channel = getChannel(did);
            if (channel == null) connect(did);
            channel = getChannel(did); // FIXME (AG): this sucks
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
                    if (wtr != null) wtr.error(new ExIOFailed("fail send packet to " + did, future.getCause()));
                }
            }
        });

        return channel; // this is the channel used to send the packets
    }

    private @Nullable Channel getChannel(DID did)
    {
        return channels.get(did);
    }

    private void addChannel(DID did, Channel channel)
    {
        Channel previous = channels.put(did, channel);
        checkState(previous == null, "" + did + " overwrote existing channel zi[ol" + (previous == null ? "null" : getZephyrClient(previous)) + " new:" + getZephyrClient(channel) + "]");
    }

    private @Nullable Channel removeChannel(DID did)
    {
        return channels.remove(did);
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
    public synchronized void signallingServiceDisconnected()
    {
        // close anyone who hasn't completed their signalling. If your signalling
        // hasn't completed, then you're going to be sitting around waiting for
        // timeouts. Instead, why not just close you right now?

        Predicate<Map.Entry<DID, Channel>> handshakeIncompletePredicate = new Predicate<Entry<DID, Channel>>()
        {
            @Override
            public boolean apply(@Nullable Entry<DID, Channel> entry)
            {
                ZephyrClientHandler zephyrClient = getZephyrClient(checkNotNull(entry).getValue());
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
                    disconnectChannel(did, new IllegalStateException("attempted renegotiation of zephyr channel to " + did, e));
                    consumeHandshake(did, handshake);
                }
            } catch (Exception e) {
                disconnectChannel(did, new ExDeviceUnavailable("fail to process signalling message from " + did, e));
            }
        }
    }

    private void consumeHandshake(DID did, ZephyrHandshake handshake)
            throws ExDeviceUnavailable, ExHandshakeFailed, ExHandshakeRenegotiation
    {
        Channel channel = getChannel(did);
        if (channel == null) { // haven't connected yet, so attempt to do so
            newChannel(did);

            channel = getChannel(did); // check if the connect failed
            if (channel == null) {
                throw new ExDeviceUnavailable("cannot reach " + did);
            }
        }

        getZephyrClient(channel).consumeHandshake(handshake);
    }

    @Override
    public synchronized void sendSignallingMessageFailed(DID did, byte[] failedmsg, Exception cause)
    {
        l.warn("{} ->sig fail err:{}", did, cause.getMessage());
        disconnectChannel(did, new ExDeviceUnavailable("failed to send zephyr handshake to " + did, cause));
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
            disconnectChannel(did, new ExDeviceUnavailable("connection service stopped"));
            return;
        }

        l.debug("{} ->sig", did);

        signallingService.sendSignallingMessage(did, bytes, this);
    }

    //
    // debugging/printing
    //

    public synchronized Collection<ZephyrDevice> getDeviceDiagnostics()
    {
        Collection<ZephyrDevice> devices = Lists.newArrayListWithCapacity(channels.size());

        for (Map.Entry<DID, Channel> entry : channels.entrySet()) {
            DID did = entry.getKey();
            Channel channel = entry.getValue();

            ZephyrClientHandler client = getZephyrClient(channel);
            ZephyrDevice device = ZephyrDevice
                    .newBuilder()
                    .setDid(did.toPB())
                    .addChannel(ZephyrChannel
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

    private static ChannelState getChannelState(ZephyrClientHandler client)
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
            s = newConnectedSocket(zephyrAddress, (int)(2 * C.SEC));
            InputStream zidInputStream = s.getInputStream();
            int bytes = zidInputStream.read(new byte[ZEPHYR_REG_MSG_LEN]);
            return bytes == ZEPHYR_REG_MSG_LEN;
        } catch (IOException e) {
            l.warn("fail zephyr reachability check", e);
            throw e;
        } finally {
            if (s != null) try {
                s.close();
            } catch (IOException e) {
                l.warn("fail close reachability socket with err:{}", e.getMessage());
            }
        }
    }
}

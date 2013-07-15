package com.aerofs.daemon.transport.xmpp.zephyr;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.daemon.transport.exception.ExDeviceDisconnected;
import com.aerofs.daemon.transport.exception.ExDeviceUnreachable;
import com.aerofs.daemon.transport.exception.ExSendFailed;
import com.aerofs.daemon.transport.lib.IConnectionServiceListener;
import com.aerofs.daemon.transport.lib.IIdentifier;
import com.aerofs.daemon.transport.lib.TransportStats;
import com.aerofs.daemon.transport.xmpp.ISignalledConnectionService;
import com.aerofs.daemon.transport.xmpp.ISignallingService;
import com.aerofs.lib.event.Prio;
import com.aerofs.proto.Files.PBDumpStat;
import com.aerofs.proto.Files.PBDumpStat.PBTransport;
import com.aerofs.rocklog.RockLog;
import com.aerofs.zephyr.client.IZephyrSignallingService;
import com.aerofs.zephyr.client.exceptions.ExHandshakeFailed;
import com.aerofs.zephyr.client.exceptions.ExHandshakeRenegotiation;
import com.aerofs.zephyr.proto.Zephyr.ZephyrControlMessage;
import com.aerofs.zephyr.proto.Zephyr.ZephyrHandshake;
import com.google.common.base.Predicate;
import com.google.protobuf.InvalidProtocolBufferException;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Proxy;
import java.net.SocketAddress;
import java.util.Enumeration;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.aerofs.daemon.lib.DaemonParam.Zephyr.HANDSHAKE_TIMEOUT;
import static com.aerofs.daemon.transport.exception.TransportDefects.DEFECT_NAME_HANDSHAKE_RENEGOTIATION;
import static com.aerofs.daemon.transport.xmpp.zephyr.ZephyrClientPipelineFactory.getZephyrClientHandler;
import static com.aerofs.zephyr.proto.Zephyr.ZephyrControlMessage.Type.HANDSHAKE;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.collect.Sets.newHashSet;
import static org.jboss.netty.buffer.ChannelBuffers.copiedBuffer;

/**
 * Creates and manages connections to a Zephyr relay server
 */
public final class ZephyrConnectionService implements ISignalledConnectionService, IZephyrSignallingService
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

    private final IIdentifier id;
    private final TransportStats transportStats;
    private final RockLog rocklog;
    private final ISignallingService signallingService;

    private final SocketAddress zephyrAddress;
    private final ClientBootstrap bootstrap;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private final Map<DID, Channel> channels = newHashMap();


    public ZephyrConnectionService(
            IIdentifier id,
            UserID localid, DID localdid,
            SSLEngineFactory clientSslEngineFactory,
            SSLEngineFactory serverSslEngineFactory,
            IConnectionServiceListener connectionServiceListener,
            ISignallingService signallingService,
            TransportStats transportStats,
            RockLog rocklog,
            ChannelFactory channelFactory, SocketAddress zephyrAddress, Proxy proxy)
    {
        this.id = id;

        this.zephyrAddress = zephyrAddress;
        this.bootstrap = new ClientBootstrap(channelFactory);
        this.bootstrap.setPipelineFactory(new ZephyrClientPipelineFactory(localid, localdid, clientSslEngineFactory, serverSslEngineFactory, transportStats, this, this, connectionServiceListener, proxy, HANDSHAKE_TIMEOUT));

        this.transportStats = transportStats;
        this.rocklog = rocklog;

        this.signallingService = signallingService;
        this.signallingService.registerSignallingClient(this);
    }

    @Override
    public String id()
    {
        return id.id();
    }

    @Override
    public int rank()
    {
        return id.rank();
    }

    //
    // startup/shutdown
    //

    @Override
    public void init()
            throws Exception
    {
        // noop
    }

    @Override
    public boolean ready()
    {
        return running.get();
    }

    @Override
    public void start()
    {
        boolean alreadyRunning = running.getAndSet(true);
        if (alreadyRunning) return;

        l.info("start");
    }

    public void stop()
    {
        boolean alreadyRunning = running.getAndSet(false);
        if (!alreadyRunning) return;

        l.info("stop");

        synchronized (this) {
            disconnectChannels(TRUE_FILTER, new ExDeviceDisconnected("connection service stopped"));
        }
    }

    //
    // IConnectionService methods
    //

    @Override
    public void linkStateChanged(final Set<NetworkInterface> removed, Set<NetworkInterface> current)
    {
        l.info("lsc");

        final Set<InetAddress> removedAddresses = newHashSet();
        for (NetworkInterface networkInterface : removed) {
            Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                removedAddresses.add(addresses.nextElement());
            }
        }

        Predicate<Entry<DID, Channel>> allChannelsFilter = new Predicate<Entry<DID, Channel>>()
        {
            @Override
            public boolean apply(@Nullable Entry<DID, Channel> entry)
            {
                InetAddress address = ((InetSocketAddress) checkNotNull(entry).getValue().getLocalAddress()).getAddress();
                return removedAddresses.contains(address);
            }
        };

        synchronized (this) {
            disconnectChannels(allChannelsFilter, new ExDeviceDisconnected("network interface down"));
        }
    }

    @Override
    public synchronized void connect(DID did)
    {
        if (!running.get()) {
            l.warn("d:{} ignore connect - connection service stopped", did);
            return;
        }

        try {
            l.info("d:{} connect", did);

            Channel channel = getChannel(did);
            if (channel != null) {
                if (getZephyrClient(channel).hasHandshakeCompleted()) {
                    // apparently the channel connected, but for some reason the
                    // upper layer doesn't think otherwise; this means that the channel
                    // isn't in a good state
                    disconnectChannel(did, new ExDeviceDisconnected("triggered by local reconnect"));
                } else {
                    // we're still in the process of handshaking - let's give it a
                    // chance. at any rate, if we fail to complete in time we'll get a
                    // timeout and it'll all be good
                    l.warn("d:{} ignore connect - handshake in progress", did);
                    return;
                }
            }

            newChannel(did);
        } catch (Exception e) {
            l.error("d:{} fail connect err:", did, e);
            if (getChannel(did) != null) {
                disconnectChannel(did, e);
            }
        }
    }

    private void newChannel(final DID did)
    {
        if (channels.containsKey(did)) { // can happen if the close future hasn't run yet
            l.warn("d:{} remove old channel for connect", did);
            removeChannel(did);
        }

        l.trace("d:{} create channel", did);

        // start by creating the channel (NOTE: this is _binding_ only, not connecting)
        // this allows us to add state before starting the connection and lifecycle process

        Channel channel = bootstrap.bind(new InetSocketAddress("0.0.0.0", 0)).getChannel();

        // set up the ZephyrClientHandler and the attachment

        ZephyrClientHandler zephyrClientHandler = getZephyrClientHandler(channel);
        zephyrClientHandler.init(did, channel);
        channel.setAttachment(zephyrClientHandler);

        // now add the channel (IMPORTANT: do the add before setting up the close future)
        // we do this because we don't want to fail in an operation and then add the failed
        // channel to the map

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
                l.debug("d:{} close future triggered", did);

                synchronized (ZephyrConnectionService.this) {
                    Channel activeChannel = getChannel(did);
                    if (activeChannel == null) {
                        l.warn("d:{} no channel", did);
                    } else if (activeChannel != future.getChannel()) {
                        l.warn("d:{} channel replaced exp:{} act:{}", did, getZephyrClient(future.getChannel()).debugString(), getZephyrClient(activeChannel).debugString());
                    } else {
                        l.info("d:{} remove zc:{}", did, getZephyrClient(future.getChannel()).debugString());
                        removeChannel(did);
                    }
                }
            }
        });

        l.trace("d:{} connecting on created channel", did);
    }

    @Override
    public synchronized void disconnect(DID did, Exception cause)
    {
        l.info("d:{} disconnect cause:{}", did, cause);
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
            l.warn("d:{} disconnect ignored - no channel", did);
            return;
        }

        getZephyrClient(channel).disconnect(cause);
    }

    @Override
    public Object send(final DID did, final IResultWaiter wtr, Prio pri, byte[][] bss, Object cke)
            throws Exception
    {
        Channel channel;

        synchronized (this) {
            channel = getChannel(did);
        }

        if (channel == null) { // can happen because the upper layer hasn't yet been notified of a disconnection
            l.warn("d:{} no channel", did);
            if (wtr != null) {
                wtr.error(new ExDeviceUnreachable(did.toStringFormal()));
            }
            return null;
        }

        if (cke != null && (cke instanceof Channel) && !cke.equals(channel)) { // this is a stream using an older channel
            l.warn("d:{} fail send - stale channel", did);
            if (wtr != null) {
                wtr.error(new ExSendFailed("stale connection"));
            }
            return null;
        }

        ChannelFuture writeFuture = channel.write(copiedBuffer(bss));
        writeFuture.addListener(new ChannelFutureListener()
        {
            @Override
            public void operationComplete(ChannelFuture future)
                    throws Exception
            {
                if (future.isSuccess()) {
                    if (wtr != null) wtr.okay();
                } else {
                    if (wtr != null) wtr.error(new ExSendFailed("fail send packet to " + did, future.getCause()));
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
        checkState(previous == null, "d:" + did + " overwrote existing channel zid:[old:" + (previous == null ? "null" : getZephyrClient(previous)) + " new:" + getZephyrClient(channel) + "]");
    }

    private @Nullable Channel removeChannel(DID did)
    {
        return channels.remove(did);
    }

    private ZephyrClientHandler getZephyrClient(Channel channel)
    {
        return checkNotNull((ZephyrClientHandler) channel.getAttachment());
    }

    //
    // ISignallingClient methods
    //

    @Override
    public void signallingServiceConnected()
            throws ExNoResource
    {
        // noop
    }

    @Override
    public synchronized void signallingServiceDisconnected()
            throws ExNoResource
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
            disconnectChannels(handshakeIncompletePredicate, new ExDeviceUnreachable("signalling service disconnected"));
        }
    }

    @Override
    public void processIncomingSignallingMessage(DID did, byte[] msg)
            throws ExNoResource
    {
        if (!running.get()) {
            l.warn("d:{} <-sig drop - service not running");
            return;
        }

        final ZephyrHandshake handshake;

        try {
            ZephyrControlMessage control = ZephyrControlMessage.parseFrom(msg);

            checkArgument(control.getType() == HANDSHAKE, "recv signalling msg with unexpected type exp:HANDSHAKE act:{}", control.getType());
            checkArgument(control.hasHandshake());

            handshake = control.getHandshake();
        } catch (InvalidProtocolBufferException e) {
            l.warn("d:{} recv invalid signalling message");
            return;
        }

        l.debug("d:{} <-sig ms:{} md:{}", did, handshake.getSourceZephyrId(), handshake.getDestinationZephyrId());

        // FIXME (AG): I'm not a fan of this entire block because of 1) notification ordering and 2) trying to be too smart
        //
        // 1) Since notifications are sent via the netty I/O thread, it's possible to get the following order: disconnected, connected
        // 2) I'm not comfortable automatically initiating a connection on your behalf

        synchronized (this) {
            try {
                try {
                    consumeHandshake(did, handshake);
                } catch (ExHandshakeRenegotiation e) {
                    rocklog.newDefect(DEFECT_NAME_HANDSHAKE_RENEGOTIATION).send();
                    disconnectChannel(did, new ExDeviceDisconnected("attempted renegotiation of zephyr channel to " + did, e));
                    consumeHandshake(did, handshake);
                }
            } catch (Exception e) {
                disconnectChannel(did, new ExDeviceDisconnected("fail to process signalling message from " + did, e));
            }
        }
    }

    private void consumeHandshake(DID did, ZephyrHandshake handshake)
            throws ExDeviceUnreachable, ExHandshakeFailed, ExHandshakeRenegotiation
    {
        Channel channel = getChannel(did);
        if (channel == null) { // haven't connected yet, so attempt to do so
            newChannel(did);

            channel = getChannel(did); // check if the connect failed
            if (channel == null) {
                throw new ExDeviceUnreachable("cannot reach " + did);
            }
        }

        getZephyrClient(channel).consumeHandshake(handshake);
    }

    @Override
    public synchronized void sendSignallingMessageFailed(DID did, byte[] failedmsg, Exception cause)
            throws ExNoResource
    {
        l.warn("d:{} ->sig fail err:", did, cause);
        disconnectChannel(did, new ExDeviceUnreachable("failed to send zephyr handshake to " + did, cause));
    }

    //
    // IZephyrSignallingService methods
    //

    @Override
    public void sendZephyrSignallingMessage(Channel sender, byte[] bytes)
    {
        DID did = getZephyrClient(sender).getRemote();

        // outgoing signalling messages are only sent when we're still in connecting.
        // as a result, the upper layer won't be told of this disconnection

        if (!running.get()) {
            l.warn("d:{} ->sig ignored - connection service stopped", did);
            disconnectChannel(did, new ExDeviceUnreachable("connection service stopped"));
            return;
        }

        l.debug("d:{} ->sig", did);

        signallingService.sendSignallingMessage(did, bytes, this);
    }

    //
    // debugging/printing
    //

    @Override
    public long getBytesReceived(final DID did)
    {
        Channel channel;

        synchronized (this) {
            channel = getChannel(did);
            if (channel == null) {
                l.warn("d:{} no channel - ignore getBytesReceived", did);
                return -1;
            }
        }

        return getZephyrClient(channel).getBytesReceived();
    }

    @Override
    public void dumpStat(PBDumpStat template, PBDumpStat.Builder builder)
            throws Exception
    {
        Map<DID, Channel> currentChannels;

        synchronized (this) {
            currentChannels = newHashMap(channels);
        }

        PBTransport tpTemplate = checkNotNull(template.getTransport(0));
        PBTransport.Builder tpBuilder = PBTransport.newBuilder();

        tpBuilder.setBytesIn(transportStats.getBytesReceived());
        tpBuilder.setBytesOut(transportStats.getBytesSent());

        if (tpTemplate.hasName()) {
            tpBuilder.setName(id());
        }

        if (tpTemplate.getConnectionCount() != 0) {
            for (Channel channel : currentChannels.values()) {
                tpBuilder.addConnection(getZephyrClient(channel).debugString());
            }
        }

        builder.addTransport(tpBuilder);
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
            throws Exception
    {
        ps.println(indent + "running:" + running.get());
    }
}

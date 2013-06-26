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
import com.aerofs.daemon.transport.lib.ITransportStats;
import com.aerofs.daemon.transport.lib.TransportStatsHandler;
import com.aerofs.daemon.transport.xmpp.ISignalledConnectionService;
import com.aerofs.daemon.transport.xmpp.ISignallingService;
import com.aerofs.lib.event.Prio;
import com.aerofs.rocklog.RockLog;
import com.aerofs.proto.Files.PBDumpStat;
import com.aerofs.proto.Files.PBDumpStat.PBTransport;
import com.aerofs.zephyr.client.IZephyrRelayedDataSink;
import com.aerofs.zephyr.client.IZephyrSignallingClient;
import com.aerofs.zephyr.client.IZephyrSignallingService;
import com.aerofs.zephyr.client.exception.ExHandshakeFailed;
import com.aerofs.zephyr.client.exception.ExHandshakeRenegotiation;
import com.aerofs.zephyr.client.pipeline.ZephyrClientPipelineFactory;
import com.aerofs.zephyr.proto.Zephyr.ZephyrControlMessage;
import com.aerofs.zephyr.proto.Zephyr.ZephyrHandshake;
import com.google.protobuf.InvalidProtocolBufferException;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.aerofs.daemon.lib.DaemonParam.Zephyr.HANDSHAKE_TIMEOUT;
import static com.aerofs.zephyr.client.pipeline.ZephyrPipeline.getRelayedHandlerName;
import static com.aerofs.zephyr.client.pipeline.ZephyrPipeline.getZephyrChannelStats;
import static com.aerofs.zephyr.client.pipeline.ZephyrPipeline.getZephyrSignallingClient;
import static com.aerofs.zephyr.client.pipeline.ZephyrPipeline.hasHandshakeCompleted;
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
public final class ZephyrConnectionService implements ISignalledConnectionService, IZephyrSignallingService, IZephyrRelayedDataSink
{
    private static final String DEFECT_NAME_HANDSHAKE_RENEGOTIATION = "net.zephyr.renegotiation";

    private static Logger l = Loggers.getLogger(ZephyrConnectionService.class);

    private final IIdentifier tpinfo;
    private final SSLEngineFactory clientSslEngineFactory;
    private final IConnectionServiceListener connectionServiceListener;
    private final ISignallingService signallingService;
    private final ITransportStats transportStats;
    private final RockLog rocklog;

    private final UserID localid;
    private final DID localdid;

    private final SocketAddress zephyrAddress;
    private final ClientBootstrap bootstrap;
    private final TransportStatsHandler transportStatsHandler;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private final Map<DID, Channel> channels = newHashMap();

    public ZephyrConnectionService(
            IIdentifier tpinfo,
            UserID localid, DID localdid,
            SSLEngineFactory clientSslEngineFactory,
            IConnectionServiceListener connectionServiceListener,
            ISignallingService signallingService,
            ITransportStats transportStats,
            RockLog rocklog,
            ChannelFactory channelFactory, SocketAddress zephyrAddress, Proxy proxy)
    {
        this.tpinfo = tpinfo;

        this.localid = localid;
        this.localdid = localdid;

        this.zephyrAddress = zephyrAddress;
        this.bootstrap = new ClientBootstrap(channelFactory);
        this.bootstrap.setPipelineFactory(new ZephyrClientPipelineFactory(this, this, proxy, HANDSHAKE_TIMEOUT));
        this.transportStatsHandler = new TransportStatsHandler(transportStats);

        this.clientSslEngineFactory = clientSslEngineFactory;
        this.connectionServiceListener = connectionServiceListener;
        this.signallingService = signallingService;
        this.signallingService.registerSignallingClient(this);

        this.transportStats = transportStats;

        this.rocklog = rocklog;
    }

    @Override
    public String id()
    {
        return tpinfo.id();
    }

    @Override
    public int rank()
    {
        return tpinfo.rank();
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

        // synchronize through this entire procedure, to avoid
        // multiple people having to close channels at the same time

        synchronized (this) {
            Map<DID, Channel> channelsToClose = newHashMap(channels); // copy to prevent ConcurrentModificationException
            for (Map.Entry<DID, Channel> entry : channelsToClose.entrySet()) {
                closeChannel(entry.getKey(), new ExDeviceDisconnected("connection service stopped"), true);
            }
        }
    }

    @Override
    public boolean ready()
    {
        return running.get();
    }

    //
    // IConnectionService methods
    //

    @Override
    public synchronized void connect(DID did)
    {
        if (!running.get()) {
            l.warn("d:{} ignore connect - connection service stopped", did);
            return;
        }

        try {
            l.info("d:{} connect", did);
            closeChannel(did, new ExDeviceDisconnected("triggered by local reconnect"), false);
            newChannel(did);
        } catch (Exception e) {
            l.error("fail connect for d:{} err:{}", did, e);

            if (getChannel(did) != null) {
                closeChannel(did, e, true);
            } else {
                connectionServiceListener.onDeviceDisconnected(did, this);
            }
        }
    }

    private void newChannel(final DID did)
    {
        l.debug("d:{} create", did);

        ChannelFuture connectFuture = bootstrap.connect(zephyrAddress);

        Channel channel = connectFuture.getChannel();
        channel.setAttachment(new ZephyrAttachment(pretty(channel), did, getZephyrChannelStats(channel)));
        channel.getPipeline().addFirst("transportStats", transportStatsHandler);
        channel.getPipeline().addBefore(getRelayedHandlerName(), "bundledssl", new CNameVerifedSSLClientHandler(localid, localdid, clientSslEngineFactory));
        addChannel(did, channel); // IMPORTANT: do the "add" _before_ setting up the close future // FIXME (AG): what do I do if we crash here?

        l.debug("d:{} create completed c:{}", did, getDebugString(channel));

        channel.getCloseFuture().addListener(new ChannelFutureListener()
        {
            @Override
            public void operationComplete(ChannelFuture future)
                    throws Exception
            {
                l.debug("d:{} close future triggered c:{}", did, getDebugString(future.getChannel()));

                synchronized (ZephyrConnectionService.this) {
                    Channel activeChannel = getChannel(did);
                    if (activeChannel == future.getChannel()) {
                        l.info("d:{} closing c:{}", did, getDebugString(future.getChannel()));
                        Channel removedChannel = removeChannel(did);
                        ZephyrAttachment zephyrAttachment = getZephyrAttachment(removedChannel);
                        if (zephyrAttachment.shouldNotifyListener()) {
                            connectionServiceListener.onDeviceDisconnected(did, ZephyrConnectionService.this);
                        }
                    }
                }
            }
        });

        connectFuture.addListener(new ChannelFutureListener() // connect failures will trigger the close future and removal
        {
            @Override
            public void operationComplete(ChannelFuture future)
                    throws Exception
            {
                Channel connectedChannel = future.getChannel();

                l.debug("d:{} connect future triggered ok:{} err:{} c:{}", did, future.isSuccess(), future.getCause(), getDebugString(connectedChannel));

                Channel channel = connectedChannel;
                if (!future.isSuccess()) {
                    closeChannel(did, new ExDeviceUnreachable("failed connect to " + did, future.getCause()), false);
                    return;
                }

                synchronized (ZephyrConnectionService.this) {
                    if (getChannel(did) == null) { // can happen because we were disconnected by the upper-layer/timeout
                        checkState(channel.getCloseFuture().isDone()); // if we've been removed, the channel should have been closed
                        return;
                    }

                    connectionServiceListener.onDeviceConnected(did, ZephyrConnectionService.this);
                    l.info("d:{} connected for duplex tx c:{}", did, getDebugString(connectedChannel));
                }
            }
        });

        l.debug("d:{} connect started", did);
    }

    @Override
    public synchronized void disconnect(DID did, Exception cause)
    {
        l.info("d:{} disconnect", did);

        closeChannel(did, cause, true);
    }

    private void closeChannel(DID did, Exception cause, boolean notifyListener)
    {
        l.debug("d:{} close e:{} n:{}", did, cause, notifyListener);

        Channel channel = getChannel(did);
        if (channel == null) {
            l.warn("d:{} close ignored - no channel", did);
            return;
        }

        if (channel.getCloseFuture().isDone()) {
            l.warn("d:{} close ignored - already closed c:{}", did, getDebugString(channel));
            return;
        }

        getZephyrAttachment(channel).setDisconnectParameters(cause, notifyListener);
        channel.close();
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

        return null;
    }

    @Override
    public void linkStateChanged(final Set<NetworkInterface> removed, Set<NetworkInterface> current)
    {
        l.info("handle lsc");

        Set<InetAddress> removedAddresses = newHashSet();
        for (NetworkInterface networkInterface : removed) {
            Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                removedAddresses.add(addresses.nextElement());
            }
        }

        Set<Channel> currentChannels;
        synchronized (this) {
            currentChannels = newHashSet(channels.values());
        }

        for(Channel channel : currentChannels) {
            InetAddress address = ((InetSocketAddress) channel.getLocalAddress()).getAddress();
            if (removedAddresses.contains(address)) {
                getZephyrAttachment(channel).setDisconnectParameters(new ExDeviceDisconnected("network interface down"), true);
                channel.close();
            }
        }
    }

    private void addChannel(DID did, Channel channel)
    {
        Channel previous = channels.put(did, channel);
        checkState(previous == null, "d:" + did + " overwrote existing channel zid:[old:" + (previous == null ? "null" : getDebugString(previous)) + " new:" + getDebugString(channel) + "]");
    }

    private @Nullable Channel removeChannel(DID did)
    {
        return channels.remove(did);
    }

    private @Nullable Channel getChannel(DID did)
    {
        return channels.get(did);
    }

    private static ZephyrAttachment getZephyrAttachment(Channel channel)
    {
        return (ZephyrAttachment) channel.getAttachment();
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
        // rationale: first, copy to avoid ConcurrentModificationException
        // then, close anyone who hasn't completed their signalling. If your signalling
        // hasn't completed, then you're going to be sitting around waiting for
        // timeouts. Instead, why not just close you right now? Also, since you haven't
        // completed your signalling (and hence, connection), we don't have to notify the
        // upper layer of a disconnection
        Map<DID, Channel> currentChannels = newHashMap(channels);
        for (Map.Entry<DID, Channel> entry : currentChannels.entrySet()) {
            if (!hasHandshakeCompleted(entry.getValue())) {
                closeChannel(entry.getKey(), new ExDeviceUnreachable("signalling service disconnected"), false);
            }
        }
    }

    @Override
    public void processIncomingSignallingMessage(DID did, byte[] msg)
            throws ExNoResource
    {
        l.debug("d:{} <-sig", did);

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

        synchronized (this) {
            if (!running.get()) {
                Channel channel = getChannel(did);
                if (channel != null) {
                    boolean handshakeCompleted = hasHandshakeCompleted(channel);
                    closeChannel(did, new ExDeviceDisconnected("connection service stopped"), handshakeCompleted);
                }
                return;
            }

            try {
                try {
                    consumeHandshake(did, handshake);
                } catch (ExHandshakeRenegotiation e) {
                    rocklog.newDefect(DEFECT_NAME_HANDSHAKE_RENEGOTIATION).send();
                    closeChannel(did, new ExDeviceDisconnected("attempted to renegotiate zephyr channel to " + did, e), false);
                    consumeHandshake(did, handshake);
                }
            } catch (Exception e) {
                closeChannel(did, new ExDeviceDisconnected("fail to process signalling message from " + did, e), true);
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
                throw new ExDeviceUnreachable("cannot reach remote device d:" + did);
            }
        }

        IZephyrSignallingClient signallingClient = getZephyrSignallingClient(channel);

        // we're getting a handshake message after the handshake has succeeded
        // this means that for some reason the remote device is trying to establish a
        // connection to us again.
        if (signallingClient == null) {
            throw new ExHandshakeRenegotiation("attempted to renegotiate zephyr handshake on incoming signalling");
        }

        signallingClient.processIncomingZephyrSignallingMessage(handshake);
    }

    @Override
    public synchronized void sendSignallingMessageFailed(DID did, byte[] failedmsg, Exception cause)
            throws ExNoResource
    {
        l.warn("d:{} ->sig fail err:{}", did, cause);

        closeChannel(did, new ExDeviceUnreachable("failed to send zephyr handshake to " + did, cause), true);
    }

    //
    // IZephyrSignallingService methods
    //

    @Override
    public void sendZephyrSignallingMessage(Channel sender, byte[] bytes)
    {
        DID did = getZephyrAttachment(sender).getRemote();

        // NOTE: outgoing signalling messages are only sent when we're still in the
        // connection phase. Since we haven't notified anyone that we've connected,
        // we shouldn't notify anyone that we've disconnected

        if (!running.get()) {
            l.warn("d:{} ->sig ignored - connection service stopped", did);
            closeChannel(did, new ExDeviceUnreachable("connection service stopped"), false);
            return;
        }

        l.debug("d:{} ->sig", did);

        signallingService.sendSignallingMessage(did, bytes, this);
    }

    //
    // IZephyrRelayedDataSink methods
    //

    @Override
    public void onDataReceived(Channel channel, ChannelBuffer data)
    {
        ZephyrAttachment attachment = getZephyrAttachment(channel);

        if (!running.get()) {
            DID did = attachment.getRemote();
            l.warn("d:{} connection service stopped - ignore relayed data and disconnect", did);
            closeChannel(did, new ExDeviceDisconnected("connection service stopped"), true);
            return;
        }

        connectionServiceListener.onIncomingMessage(attachment.getRemote(), attachment.getUserID(),
                new ChannelBufferInputStream(data), data.readableBytes());
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

        return checkNotNull(getZephyrAttachment(channel)).getChannelStats().getBytesReceived();
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
                tpBuilder.addConnection(getDebugString(channel));
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

    private String getDebugString(Channel channel)
    {
        ZephyrAttachment attachment = getZephyrAttachment(channel);
        return String.format("[(%s) %s -> %s : tx=%d rx=%d a=%s]",
                attachment.getId(),
                localdid,
                attachment.getRemote(),
                attachment.getChannelStats().getBytesSent(),
                attachment.getChannelStats().getBytesReceived(),
                attachment.getDisconnectCause());
    }

    private static String pretty(Channel c) // FIXME (AG): copied from verkehr
    {
        String hex = Integer.toHexString(c.getId());
        return String.format("0x%1$8s", hex).replace(' ', '0');
    }
}

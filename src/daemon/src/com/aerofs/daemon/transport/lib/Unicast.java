package com.aerofs.daemon.transport.lib;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.daemon.lib.DaemonParam.Jingle;
import com.aerofs.daemon.link.ILinkStateListener;
import com.aerofs.daemon.transport.ExDeviceDisconnected;
import com.aerofs.daemon.transport.lib.handlers.ClientHandler;
import com.aerofs.daemon.transport.lib.handlers.IOStatsHandler;
import com.aerofs.daemon.transport.lib.handlers.ServerHandler;
import com.aerofs.daemon.transport.lib.handlers.ServerHandler.IServerHandlerListener;
import com.aerofs.lib.IDumpStat;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.ex.ExDeviceOffline;
import com.aerofs.proto.Diagnostics.PBDumpStat;
import com.aerofs.proto.Diagnostics.PBDumpStat.PBTransport;
import com.aerofs.proto.Transport.PBTPHeader;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public final class Unicast implements ILinkStateListener, IUnicastInternal, IServerHandlerListener, IDumpStat
{
    private static final Logger l = Loggers.getLogger(Unicast.class);

    private final ConcurrentMap<DID, ClientHandler> clients = Maps.newConcurrentMap();
    private final ConcurrentMap<DID, ServerHandler> servers = Maps.newConcurrentMap();
    private final IUnicastCallbacks unicastCallbacks;
    private final TransportStats transportStats;

    private ServerBootstrap serverBootstrap;
    private ClientBootstrap clientBootstrap;
    private IUnicastListener unicastListener;

    private Channel serverChannel;
    private volatile boolean paused;
    private volatile boolean started;

    public Unicast(IUnicastCallbacks unicastCallbacks, TransportStats transportStats)
    {
        this.unicastCallbacks = unicastCallbacks;
        this.transportStats = transportStats;
    }

    // NOTE: these fields cannot be final due to a
    // circular dependency between the TCP and the underlying handlers
    // FIXME (AG): consider using the ZephyrUnicastProxy to resolve this
    public void setBootstraps(ServerBootstrap serverBootstrap, ClientBootstrap clientBootstrap)
    {
        this.serverBootstrap = serverBootstrap;
        this.clientBootstrap = clientBootstrap;
    }

    public void setUnicastListener(IUnicastListener unicastListener)
    {
        this.unicastListener = unicastListener;
    }

    public void start(SocketAddress address)
    {
        if (started) return;

        synchronized (this) {
            if (started) return;

            checkState(serverChannel == null);
            serverChannel = serverBootstrap.bind(address);

            started = true;
        }

        unicastListener.onUnicastReady();
    }

    @Override
    public void onLinkStateChanged(
            ImmutableSet<NetworkInterface> previous,
            ImmutableSet<NetworkInterface> current,
            ImmutableSet<NetworkInterface> added,
            ImmutableSet<NetworkInterface> removed)
    {
        boolean becameLinkDown = !previous.isEmpty() && current.isEmpty();

        Set<DID> connectedDIDs = Sets.newHashSet();
        connectedDIDs.addAll(clients.keySet());
        connectedDIDs.addAll(servers.keySet());

        if (becameLinkDown) {
            pauseAccept();

            // FIXME (AG): REMOVE THIS IMMEDIATELY!
            // I put this in because this can cause a crash inside jingle
            // especially because the resource ownership between jingle and signalthread is broken
            if (unicastCallbacks instanceof Jingle) {
                return;
            }

            for (DID did : connectedDIDs) {
                disconnect(did, new ExDeviceDisconnected("link down"));
            }
        }

        if (previous.isEmpty() && !current.isEmpty()) {
            resumeAccept();
        }
    }

    private void pauseAccept()
    {
        if (!started) return;

        unicastListener.onUnicastUnavailable();
        serverChannel.setReadable(false);
        paused = true;
        l.info("pause unicast accept");
    }

    private void resumeAccept()
    {
        if (!started) return;

        paused = false;
        serverChannel.setReadable(true);
        unicastListener.onUnicastReady();
        l.info("resume unicast accept");
    }

    /**
     * Called by the ServerHandler when the server has accepted a connection form a remote client
     */
    @Override
    public void onIncomingChannel(final DID did, Channel channel)
    {
        final ServerHandler server = channel.getPipeline().get(ServerHandler.class);
        channel.setAttachment(new DIDWrapper(did));

        // If we have just paused syncing, disconnect.
        if (paused) {
            server.disconnect();
            return;
        }

        ServerHandler old = servers.put(did, checkNotNull(server));

        if (old != null) old.disconnect();

        channel.getCloseFuture().addListener(new ChannelFutureListener()
        {
            @Override
            public void operationComplete(ChannelFuture channelFuture)
                    throws Exception
            {
                // Remove only if its still the same server in the map.
                if (servers.remove(did, server)) {
                    l.info("remove incoming connection from did:{}", did);

                    if (!clients.containsKey(did)) {
                        unicastListener.onDeviceDisconnected(did);
                    }
                }
            }
        });
    }

    /**
     * Disconnects from a remote peer.
     */
    @Override
    public void disconnect(DID did, Exception cause)
    {
        l.info("disconnect from did:{} cause:{}", did, cause);

        ClientHandler client = clients.get(did);
        if (client != null) {
            client.disconnect();
        }

        ServerHandler server = servers.get(did);
        if (server != null) {
            server.disconnect();
        }
    }

    /**
     * @return address on which the server can accept incoming connections.
     */
    public SocketAddress getListeningAddress()
    {
        return serverChannel.getLocalAddress();
    }

    @Override
    public Object send(final DID did, final @Nullable IResultWaiter wtr, Prio pri, byte[][] bss, Object cookie)
        throws ExDeviceOffline
    {
        // Use the ClientHandler as the cookie to send the packet if the cookie is present.
        // This is to bind an outgoing stream to a particular connection, needed for the
        // following scenario:
        //
        // 1. A and B have two or more ethernet links connected to each other.
        // 2. A receives B's pong message from one link, and add the IP address to its ARP
        // 3. A starts sending a stream to B
        // 4. A receives B's pong from another link, and in turn update the APR
        // 5. the rest of the chunks in the stream will be sent via the latter link, which violates
        // streams' guarantee of in-order delivery.

        ClientHandler client = (cookie == null) ? clients.get(did) : (ClientHandler) cookie;
        if (client == null) client = newConnection(did);

        ListenableFuture<Void> future = client.send(bss);

        Futures.addCallback(future, new FutureCallback<Void>()
        {
            @Override
            public void onSuccess(Void v)
            {
                if (wtr != null) wtr.okay();
            }

            @Override
            public void onFailure(Throwable throwable)
            {
                if (wtr != null) {
                    if (throwable instanceof Exception) {
                        wtr.error((Exception)throwable);
                    } else {
                        SystemUtil.fatal(throwable);
                    }
                }
            }
        });

        return client;
    }

    public void sendControl(DID did, PBTPHeader h)
            throws ExDeviceOffline
    {
        send(did, null, Prio.LO, TPUtil.newControl(h), null);
    }

    /**
     * Creates a new client connected to the specified did
     * @throws ExDeviceOffline if the arp table doesn't contain an entry for the did
     */
    private ClientHandler newConnection(final DID did)
            throws ExDeviceOffline
    {
        SocketAddress remoteAddress = unicastCallbacks.resolve(did);
        Channel channel = clientBootstrap.connect(remoteAddress).getChannel();
        channel.setAttachment(new DIDWrapper(did));

        final ClientHandler client = channel.getPipeline().get(ClientHandler.class);
        l.debug("registering new connection to did:{}", did);
        clients.put(did, checkNotNull(client));

        client.setExpectedRemoteDid(did);

        // Remove the client when the channel is closed
        channel.getCloseFuture().addListener(new ChannelFutureListener()
        {
            @Override
            public void operationComplete(ChannelFuture channelFuture)
                    throws Exception
            {
                // Remove only if its still the same client in the map.
                if (clients.remove(did, client)) {
                    l.info("remove outgoing connection to did:{}", did);

                    if (!servers.containsKey(did)) {
                        unicastListener.onDeviceDisconnected(did);
                    }
                }
            }
        });

        unicastCallbacks.onClientCreated(client);

        return client;
    }

    @Override
    public void dumpStat(PBDumpStat template, PBDumpStat.Builder builder)
        throws Exception
    {
        PBTransport tp = checkNotNull(template.getTransport(0));

        // get the PBTransport builder
        int lastBuilderIdx = builder.getTransportBuilderList().size();
        checkState(lastBuilderIdx >= 1);
        PBTransport.Builder tpbuilder = builder.getTransportBuilder(lastBuilderIdx - 1);

        // Add global bytes sent / received stats
        tpbuilder.setBytesIn(transportStats.getBytesReceived());
        tpbuilder.setBytesOut(transportStats.getBytesSent());

        // Add stats about individual connections
        if (tp.getConnectionCount() != 0) {

            Set<DID> dids = Sets.union(clients.keySet(), servers.keySet());

            for (DID did : dids) {
                ClientHandler client = clients.get(did);
                ServerHandler server = servers.get(did);

                long sent = (client != null) ? client.getPipeline().get(IOStatsHandler.class).getBytesSentOnChannel() : 0;
                long rcvd = (server != null) ? server.getPipeline().get(IOStatsHandler.class).getBytesReceivedOnChannel() : 0;

                tpbuilder.addConnection(did + " : sent: " + Long.toString(sent) + ", rcvd: " + Long.toString(rcvd));
            }
        }

        builder.setTransport(lastBuilderIdx, tpbuilder);
    }
}

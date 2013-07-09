package com.aerofs.daemon.transport.tcp;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.daemon.transport.lib.IPipeDebug;
import com.aerofs.daemon.transport.lib.IUnicast;
import com.aerofs.daemon.transport.lib.TPUtil;
import com.aerofs.daemon.transport.lib.TransportStats;
import com.aerofs.daemon.transport.lib.handlers.IOStatsHandler;
import com.aerofs.daemon.transport.tcp.TCPServerHandler.ITCPServerHandlerListener;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.ex.ExDeviceOffline;
import com.aerofs.proto.Files.PBDumpStat;
import com.aerofs.proto.Files.PBDumpStat.PBTransport;
import com.aerofs.proto.Transport.PBTPHeader;
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
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

class Unicast implements IUnicast, IPipeDebug, ITCPServerHandlerListener
{
    private static final Logger l = Loggers.getLogger(Unicast.class);
    private static final int PORT_ANY = 0;

    private final ITCP _tcp;
    private final ARP _arp;
    private final Stores _stores;
    private final TransportStats _transportStats;
    private final ClientBootstrap _clientBootstrap;
    private final ServerBootstrap _serverBootstrap;
    private final ConcurrentMap<DID, TCPClientHandler> _clients = Maps.newConcurrentMap();
    private final ConcurrentMap<DID, TCPServerHandler> _servers = Maps.newConcurrentMap();
    private Channel _serverChannel;
    private volatile boolean _paused;

    Unicast(ITCP tcp, ARP arp, Stores stores, ServerSocketChannelFactory serverChannelFactory,
            ClientSocketChannelFactory clientChannelFactory, TransportStats ts)
    {
        _tcp = tcp;
        _arp = arp;
        _stores = stores;
        _transportStats = ts;

        BootstrapFactory bsFact = new BootstrapFactory(ts);
        _serverBootstrap = bsFact.newServerBootstrap(serverChannelFactory, this, tcp);
        _clientBootstrap = bsFact.newClientBootstrap(clientChannelFactory);
    }

    void start()
    {
        synchronized (this) {
            checkState(_serverChannel == null);
            _serverChannel = _serverBootstrap.bind(new InetSocketAddress(PORT_ANY));
            l.info("listening to {}", getListeningPort());
        }
    }

    public void pauseAccept()
    {
        _paused = true;
        _serverChannel.setReadable(false);
        l.info("server paused. (port: {})", getListeningPort());
    }

    public void resumeAccept()
    {
        _paused = false;
        _serverChannel.setReadable(true);
        l.info("server resumed");
    }

    /**
     * Called by TCPServerHandler when the server has accepted a connection form a remote client
     */
    @Override
    public void onIncomingChannel(final DID did, Channel channel)
    {
        final TCPServerHandler server = channel.getPipeline().get(TCPServerHandler.class);

        // If we have just paused syncing, disconnect.
        if (_paused) {
            server.disconnect();
            return;
        }

        TCPServerHandler old = _servers.put(did, checkNotNull(server));

        if (old != null) old.disconnect();

        channel.getCloseFuture().addListener(new ChannelFutureListener()
        {
            @Override
            public void operationComplete(ChannelFuture channelFuture)
                    throws Exception
            {
                l.info("Server disconnected. did: {}", did);
                // Remove only if its still the same server in the map.
                _servers.remove(did, server);
                _tcp.closePeerStreams(did, false, true);
            }
        });
    }

    /**
     * Disconnects from a remote peer.
     */
    void disconnect(DID did)
    {
        TCPClientHandler client = _clients.get(did);
        if (client != null) client.disconnect();

        TCPServerHandler server = _servers.get(did);
        if (server != null) server.disconnect();
    }

    /**
     * Whether we are connected to a remote peer
     *
     * Note: this only checks if we have an outgoing connection to a peer. Therefore, there is
     * technically a possibility that ARP might incorrectly do garbage collection if we have an
     * incoming connection but no outgoing connection and we have not received a multicast ping for
     * an interval greater than arp gc time. This would be pretty unlikely.
     */
    boolean isConnected(DID did)
    {
        TCPClientHandler client = _clients.get(did);
        return client != null && client.isConnected();
    }

    /**
     * @return the port that the tcp server is listening to
     */
    int getListeningPort()
    {
        return ((InetSocketAddress)_serverChannel.getLocalAddress()).getPort();
    }

    @Override
    public long getBytesReceived(DID did)
    {
        TCPServerHandler server = _servers.get(did);
        if (server == null) return 0;

        IOStatsHandler stats = server.getPipeline().get(IOStatsHandler.class);
        return stats.getBytesReceivedOnChannel();
    }

    @Override
    public Object send(final DID did, final IResultWaiter wtr, Prio pri, byte[][] bss, Object cookie)
        throws ExDeviceOffline, ExNoResource, IOException
    {
        // Use the TCPClientHandler as the cookie to send the packet if the cookie is present.
        // This is to bind an outgoing stream to a particular TCP connection, needed for the
        // following scenario:
        //
        // 1. A and B have two or more ethernet links connected to each other.
        // 2. A receives B's pong message from one link, and add the IP address to its ARP
        // 3. A starts sending a stream to B
        // 4. A receives B's pong from another link, and in turn update the APR
        // 5. the rest of the chunks in the stream will be sent via the latter link, which violates
        // streams' guarantee of in-order delivery.

        TCPClientHandler client = (cookie == null) ? _clients.get(did) : (TCPClientHandler) cookie;
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

    /**
     * Creates a new client connected to the specified did
     * @throws ExDeviceOffline if the arp table doesn't contain an entry for the did
     */
    private TCPClientHandler newConnection(final DID did)
            throws ExDeviceOffline
    {
        InetSocketAddress remoteAddress = _arp.getThrows(did)._isa;
        Channel channel = _clientBootstrap.connect(remoteAddress).getChannel();
        final TCPClientHandler client = channel.getPipeline().get(TCPClientHandler.class);
        l.debug("registering new connection to did:{}", did);
        _clients.put(did, checkNotNull(client));

        client.setExpectedRemoteDid(did);

        // Remove the client when the channel is closed
        channel.getCloseFuture().addListener(new ChannelFutureListener()
        {
            @Override
            public void operationComplete(ChannelFuture channelFuture)
                    throws Exception
            {
                // Remove only if its still the same client in the map.
                l.debug("removing connection to did:{}", did);
                _clients.remove(did, client);
                _tcp.closePeerStreams(did, true, false);
            }
        });

        // Send a TCP_PONG so that the peer knows our listening port and our stores
        PBTPHeader pong = _stores.newPongMessage(true);
        if (pong != null) sendControl(did, pong);

        return client;
    }

    public void sendControl(DID did, PBTPHeader h)
        throws ExDeviceOffline
    {
        TCPClientHandler client = _clients.get(did);
        if (client == null) throw new ExDeviceOffline();

        byte[][] bss = TPUtil.newControl(h);
        client.send(bss);
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
        tpbuilder.setBytesIn(_transportStats.getBytesReceived());
        tpbuilder.setBytesOut(_transportStats.getBytesSent());

        // Add arp
        if (tp.hasDiagnosis()) tpbuilder.setDiagnosis("arp:\n" + _arp);

        // Add stats about individual connections
        if (tp.getConnectionCount() != 0) {

            Set<DID> dids = Sets.union(_clients.keySet(), _servers.keySet());

            for (DID did : dids) {
                TCPClientHandler client = _clients.get(did);
                TCPServerHandler server = _servers.get(did);

                long sent = (client != null) ? client.getPipeline().get(IOStatsHandler.class).getBytesSentOnChannel() : 0;
                long rcvd = (server != null) ? server.getPipeline().get(IOStatsHandler.class).getBytesReceivedOnChannel() : 0;

                tpbuilder.addConnection(did + " : sent: " + Long.toString(sent) + ", rcvd: " + Long.toString(rcvd));
            }
        }

        builder.setTransport(lastBuilderIdx, tpbuilder);
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
    {
        // empty - nothing to add to dumpStatMisc
    }
}

/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.lib.BlockingPrioQueue;
import com.aerofs.daemon.mobile.MobileServerZephyrConnector;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.jingle.Jingle;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.daemon.transport.tcp.TCP;
import com.aerofs.daemon.transport.zephyr.Zephyr;
import com.aerofs.lib.event.IEvent;
import com.aerofs.rocklog.RockLog;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.net.Proxy;

public final class TransportFactory
{
    public static enum TransportType
    {
        LANTCP("t", 0),
        JINGLE("j", 1),
        ZEPHYR("z", 2);

        private final String id;
        private final int rank;

        private TransportType(String id, int rank)
        {
            this.id = id;
            this.rank = rank;
        }

        public String getId()
        {
            return id;
        }

        public int getRank()
        {
            return rank;
        }
    }

    public static class ExUnsupportedTransport extends Exception
    {
        private static final long serialVersionUID = 0L;

        public ExUnsupportedTransport(String unsupportedTransportName)
        {
            super("invalid transport:" + unsupportedTransportName);
        }
    }

    private String absRtRoot;
    private final UserID userID;
    private final DID did;
    private final byte[] scrypted;
    private final boolean listenToMulticastOnLoopback;
    private final InetSocketAddress stunServerAddress;
    private final InetSocketAddress xmppServerAddress;
    private final String xmppServerDomain;
    private final InetSocketAddress zephyrServerAddress;
    private final Proxy proxy;
    private final BlockingPrioQueue<IEvent> transportEventSink;
    private final @Nullable MobileServerZephyrConnector mobileServerZephyrConnector;
    private final RockLog rocklog;
    private final ServerSocketChannelFactory serverSocketChannelFactory;
    private final ClientSocketChannelFactory clientSocketChannelFactory;
    private final MaxcastFilterReceiver maxcastFilterReceiver;
    private final SSLEngineFactory serverSslEngineFactory;
    private final SSLEngineFactory clientSslEngineFactory;

    public TransportFactory(
            String absRtRoot,
            UserID userID,
            DID did,
            byte[] scrypted,
            boolean listenToMulticastOnLoopback,
            InetSocketAddress stunServerAddress,
            InetSocketAddress xmppServerAddress,
            String xmppServerDomain,
            InetSocketAddress zephyrServerAddress,
            Proxy proxy,
            BlockingPrioQueue<IEvent> transportEventSink,
            RockLog rocklog,
            MaxcastFilterReceiver maxcastFilterReceiver,
            @Nullable MobileServerZephyrConnector mobileServerZephyrConnector,
            ClientSocketChannelFactory clientSocketChannelFactory,
            ServerSocketChannelFactory serverSocketChannelFactory,
            SSLEngineFactory clientSslEngineFactory,
            SSLEngineFactory serverSslEngineFactory)
    {
        this.absRtRoot = absRtRoot;
        this.userID = userID;
        this.did = did;
        this.scrypted = scrypted;
        this.listenToMulticastOnLoopback = listenToMulticastOnLoopback;
        this.stunServerAddress = stunServerAddress;
        this.xmppServerAddress = xmppServerAddress;
        this.xmppServerDomain = xmppServerDomain;
        this.zephyrServerAddress = zephyrServerAddress;
        this.proxy = proxy;
        this.transportEventSink = transportEventSink;
        this.rocklog = rocklog;
        this.maxcastFilterReceiver = maxcastFilterReceiver;
        this.clientSocketChannelFactory = clientSocketChannelFactory;
        this.serverSocketChannelFactory = serverSocketChannelFactory;
        this.clientSslEngineFactory = clientSslEngineFactory;
        this.serverSslEngineFactory = serverSslEngineFactory;
        this.mobileServerZephyrConnector = mobileServerZephyrConnector;
    }

    public ITransport newTransport(TransportType transportType, String transportId, int transportRank)
            throws ExUnsupportedTransport
    {
        switch (transportType) {
        case LANTCP:
            return newLanTcp(transportId, transportRank);
        case ZEPHYR:
            return newZephyr(transportId, transportRank);
        case JINGLE:
            return newJingle(transportId, transportRank);
        default:
            throw new ExUnsupportedTransport(transportType.name());
        }
    }

    public ITransport newTransport(TransportType transportType)
            throws ExUnsupportedTransport
    {
        return newTransport(transportType, transportType.getId(), transportType.getRank());
    }

    private ITransport newLanTcp(String transportId, int transportRank)
    {
        return new TCP(
                userID,
                did,
                transportId,
                transportRank,
                transportEventSink,
                listenToMulticastOnLoopback,
                maxcastFilterReceiver,
                clientSslEngineFactory,
                serverSslEngineFactory,
                clientSocketChannelFactory,
                serverSocketChannelFactory);
    }

    private ITransport newZephyr(String transportId, int transportRank)
    {
        return new Zephyr(
                userID,
                did,
                scrypted,
                transportId,
                transportRank,
                transportEventSink,
                maxcastFilterReceiver,
                clientSslEngineFactory,
                serverSslEngineFactory,
                clientSocketChannelFactory,
                mobileServerZephyrConnector,
                rocklog,
                xmppServerAddress,
                xmppServerDomain,
                zephyrServerAddress,
                proxy);
    }

    private ITransport newJingle(String transportId, int transportRank)
    {
        return new Jingle(
                userID,
                did,
                stunServerAddress,
                xmppServerAddress,
                xmppServerDomain,
                scrypted,
                absRtRoot,
                transportId,
                transportRank,
                transportEventSink,
                maxcastFilterReceiver,
                rocklog,
                clientSslEngineFactory,
                serverSslEngineFactory);
    }
}

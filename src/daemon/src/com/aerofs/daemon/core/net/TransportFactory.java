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
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.daemon.transport.tcp.TCP;
import com.aerofs.daemon.transport.xmpp.Jingle;
import com.aerofs.daemon.transport.xmpp.Zephyr;
import com.aerofs.lib.cfg.CfgAbsRTRoot;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.cfg.CfgScrypted;
import com.aerofs.lib.event.IEvent;
import com.aerofs.rocklog.RockLog;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.net.Proxy;

import static com.aerofs.daemon.core.net.TransportFactory.Transport.JINGLE;
import static com.aerofs.daemon.core.net.TransportFactory.Transport.LANTCP;
import static com.aerofs.daemon.core.net.TransportFactory.Transport.ZEPHYR;

public final class TransportFactory
{
    public static enum Transport
    {
        LANTCP("t", 0),
        ZEPHYR("z", 1),
        JINGLE("j", 2),;

        private final String id;
        private final int rank;

        private Transport(String id, int rank)
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
    private final UserID localid;
    private final DID localdid;
    private byte[] scrypted;
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
            CfgAbsRTRoot absRtRoot,
            CfgLocalUser localid,
            CfgLocalDID localdid,
            CfgScrypted scrypted,
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
        this.absRtRoot = absRtRoot.get();
        this.localid = localid.get();
        this.localdid = localdid.get();
        this.scrypted = scrypted.get();
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

    public ITransport newTransport(Transport transport)
            throws ExUnsupportedTransport
    {
        switch (transport) {
        case LANTCP:
            return newLanTcp();
        case ZEPHYR:
            return newZephyr();
        case JINGLE:
            return newJingle();
        default:
            throw new ExUnsupportedTransport(transport.name());
        }
    }

    private ITransport newLanTcp()
    {
        return new TCP(
                localid,
                localdid,
                LANTCP.getId(),
                LANTCP.getRank(),
                transportEventSink,
                maxcastFilterReceiver,
                clientSslEngineFactory,
                serverSslEngineFactory,
                clientSocketChannelFactory,
                serverSocketChannelFactory);
    }

    private ITransport newZephyr()
    {
        return new Zephyr(
                localid,
                localdid,
                scrypted,
                ZEPHYR.getId(),
                ZEPHYR.getRank(),
                transportEventSink,
                maxcastFilterReceiver,
                clientSslEngineFactory,
                serverSslEngineFactory,
                clientSocketChannelFactory,
                mobileServerZephyrConnector,
                rocklog,
                zephyrServerAddress,
                proxy);
    }

    private ITransport newJingle()
    {
        return new Jingle(
                localdid,
                scrypted,
                absRtRoot,
                JINGLE.getId(),
                JINGLE.getRank(),
                transportEventSink,
                maxcastFilterReceiver,
                rocklog);
    }
}

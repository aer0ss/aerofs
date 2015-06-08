/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.daemon.transport.ISignallingService;
import com.aerofs.daemon.transport.zephyr.ZephyrParams;
import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.lib.BlockingPrioQueue;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.IRoundTripTimes;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.daemon.transport.tcp.TCP;
import com.aerofs.daemon.transport.zephyr.Zephyr;
import com.aerofs.lib.event.IEvent;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.util.Timer;

import java.net.Proxy;

public final class TransportFactory
{
    public enum TransportType
    {
        LANTCP("t", 0),
        ZEPHYR("z", 2);

        private final String id;
        private final int rank;

        TransportType(String id, int rank)
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

    private final UserID userID;
    private final DID did;
    private final long streamTimeout;
    private final boolean listenToMulticastOnLoopback;
    private final long channelConnectTimeout;
    private final long heartbeatInterval;
    private final int maxFailedHeartbeats;
    private final long zephyrHandshakeTimeout;
    private final ZephyrParams zephyrParams;
    private final Proxy proxy;
    private final Timer timer;
    private final BlockingPrioQueue<IEvent> transportEventSink;
    private final LinkStateService linkStateService;
    private final ServerSocketChannelFactory serverSocketChannelFactory;
    private final ClientSocketChannelFactory clientSocketChannelFactory;
    private final MaxcastFilterReceiver maxcastFilterReceiver;
    private final SSLEngineFactory serverSslEngineFactory;
    private final SSLEngineFactory clientSslEngineFactory;
    private final IRoundTripTimes roundTripTimes;
    private final ISignallingService signalling;

    public TransportFactory(
            UserID userID,
            DID did,
            long streamTimeout,
            boolean listenToMulticastOnLoopback,
            long channelConnectTimeout,
            long hearbeatInterval,
            int maxFailedHeartbeats,
            long zephyrHandshakeTimeout,
            ZephyrParams zephyrParams,
            Proxy proxy,
            Timer timer,
            BlockingPrioQueue<IEvent> transportEventSink,
            LinkStateService linkStateService,
            MaxcastFilterReceiver maxcastFilterReceiver,
            ClientSocketChannelFactory clientSocketChannelFactory,
            ServerSocketChannelFactory serverSocketChannelFactory,
            SSLEngineFactory clientSslEngineFactory,
            SSLEngineFactory serverSslEngineFactory,
            IRoundTripTimes roundTripTimes,
            ISignallingService signalling)
    {
        this.userID = userID;
        this.did = did;
        this.streamTimeout = streamTimeout;
        this.listenToMulticastOnLoopback = listenToMulticastOnLoopback;
        this.channelConnectTimeout = channelConnectTimeout;
        this.heartbeatInterval = hearbeatInterval;
        this.maxFailedHeartbeats = maxFailedHeartbeats;
        this.zephyrHandshakeTimeout = zephyrHandshakeTimeout;
        this.zephyrParams = zephyrParams;
        this.proxy = proxy;
        this.timer = timer;
        this.transportEventSink = transportEventSink;
        this.linkStateService = linkStateService;
        this.maxcastFilterReceiver = maxcastFilterReceiver;
        this.clientSocketChannelFactory = clientSocketChannelFactory;
        this.serverSocketChannelFactory = serverSocketChannelFactory;
        this.clientSslEngineFactory = clientSslEngineFactory;
        this.serverSslEngineFactory = serverSslEngineFactory;
        this.roundTripTimes = roundTripTimes;
        this.signalling = signalling;
    }

    public ITransport newTransport(TransportType transportType, String transportId, int transportRank)
            throws ExUnsupportedTransport
    {
        switch (transportType) {
        case LANTCP:
            return newLanTcp(transportId, transportRank);
        case ZEPHYR:
            return newZephyr(transportId, transportRank);
        default:
            throw new ExUnsupportedTransport(transportType.name());
        }
    }

    public ITransport newTransport(TransportType transportType)
            throws ExUnsupportedTransport
    {
        return newTransport(transportType, transportType.getId(), transportType.getRank());
    }

    private TCP newLanTcp(String transportId, int transportRank)
    {
        return new TCP(
                userID,
                did,
                streamTimeout,
                transportId,
                transportRank,
                transportEventSink,
                linkStateService,
                listenToMulticastOnLoopback,
                channelConnectTimeout,
                heartbeatInterval,
                maxFailedHeartbeats,
                maxcastFilterReceiver,
                clientSslEngineFactory,
                serverSslEngineFactory,
                timer,
                clientSocketChannelFactory,
                serverSocketChannelFactory,
                roundTripTimes);
    }

    private ITransport newZephyr(String transportId, int transportRank)
    {
        return new Zephyr(
                userID,
                did,
                streamTimeout,
                transportId,
                transportRank,
                transportEventSink,
                linkStateService,
                clientSslEngineFactory,
                serverSslEngineFactory,
                clientSocketChannelFactory,
                timer,
                heartbeatInterval,
                maxFailedHeartbeats,
                zephyrHandshakeTimeout,
                zephyrParams,
                proxy,
                roundTripTimes,
                signalling);
    }
}

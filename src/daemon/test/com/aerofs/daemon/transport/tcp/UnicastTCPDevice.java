/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.tcp;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.ssl.IPrivateKeyProvider;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.base.ssl.SSLEngineFactory.Mode;
import com.aerofs.base.ssl.SSLEngineFactory.Platform;
import com.aerofs.daemon.lib.BlockingPrioQueue;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.MockCA;
import com.aerofs.daemon.transport.PrivateKeyProvider;
import com.aerofs.daemon.transport.TransportReader;
import com.aerofs.daemon.transport.lib.IAddressResolver;
import com.aerofs.daemon.transport.lib.IRoundTripTimes;
import com.aerofs.daemon.transport.lib.SemaphoreTriggeringListener;
import com.aerofs.daemon.transport.lib.StreamManager;
import com.aerofs.daemon.transport.lib.TransportStats;
import com.aerofs.daemon.transport.lib.Unicast;
import com.aerofs.daemon.transport.lib.UnicastTransportListener;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler.ChannelMode;
import com.aerofs.daemon.transport.lib.handlers.TransportProtocolHandler;
import com.aerofs.lib.event.IEvent;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.util.Timer;

import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.Random;

import static com.aerofs.daemon.lib.DaemonParam.HEARTBEAT_INTERVAL;
import static com.aerofs.daemon.lib.DaemonParam.MAX_FAILED_HEARTBEATS;
import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public final class UnicastTCPDevice
{
    public DID did = DID.generate();
    public LinkStateService linkStateService = new LinkStateService();
    public BlockingPrioQueue<IEvent> outgoingEventSink = new BlockingPrioQueue<IEvent>(100);
    public UserID userID;
    public InetSocketAddress listeningAddress; // <----- UNIQUE TO TCP AND REQUIRED BY TESTS
    public IAddressResolver addressResolver; // <----- UNIQUE TO TCP AND REQUIRED BY TESTS
    public SemaphoreTriggeringListener unicastListener;
    public Unicast unicast;
    public UnicastTransportListener transportListener;
    public TransportReader transportReader;

    private final TCPBootstrapFactory tcpBootstrapFactory;
    private final ChannelTeardownHandler clientChannelTeardownHandler;
    private final ChannelTeardownHandler serverChannelTeardownHandler;

    // interesting...this is practically all the wiring in TCP
    // TODO (AG): find some way to extract this so that we can start up only the unicast/multicast side and use this inside the real transport
    public UnicastTCPDevice(
            long channelConnectTimeout,
            Random random,
            SecureRandom secureRandom,
            MockCA mockCA,
            UnicastTransportListener transportListener)
            throws Exception
    {
        this.transportListener = transportListener;

        String transportId = String.format("t-%d", random.nextInt(10));
        ITransport transport = mock(ITransport.class);
        when(transport.id()).thenReturn(transportId);

        Stores stores = mock(Stores.class);
        addressResolver = mock(IAddressResolver.class);
        unicastListener = spy(new SemaphoreTriggeringListener());

        userID = UserID.fromExternal(String.format("user%d@arrowfs.org", random.nextInt(10)));
        listeningAddress = new InetSocketAddress(random.nextInt(30000) + 10000);

        TransportStats transportStats = new TransportStats();
        StreamManager streamManager = new StreamManager();

        unicast = new Unicast(addressResolver, transport);
        unicast.setUnicastListener(unicastListener);
        linkStateService.addListener(unicast, sameThreadExecutor());

        TCPProtocolHandler tcpProtocolHandler = new TCPProtocolHandler(stores, unicast);
        TransportProtocolHandler transportProtocolHandler = new TransportProtocolHandler(transport, outgoingEventSink, streamManager);
        clientChannelTeardownHandler = new ChannelTeardownHandler(transport, outgoingEventSink, streamManager, ChannelMode.CLIENT);
        serverChannelTeardownHandler = new ChannelTeardownHandler(transport, outgoingEventSink, streamManager, ChannelMode.SERVER);

        IPrivateKeyProvider privateKeyProvider = new PrivateKeyProvider(secureRandom, BaseSecUtil.getCertificateCName(userID, did), mockCA.getCaName(), mockCA.getCACertificateProvider().getCert(), mockCA.getCaKeyPair().getPrivate());
        SSLEngineFactory clientSSLEngineFactory = new SSLEngineFactory(Mode.Client, Platform.Desktop, privateKeyProvider, mockCA.getCACertificateProvider(), null);
        SSLEngineFactory serverSSLEngineFactory = new SSLEngineFactory(Mode.Server, Platform.Desktop, privateKeyProvider, mockCA.getCACertificateProvider(), null);
        tcpBootstrapFactory =  new TCPBootstrapFactory(
                userID,
                did,
                channelConnectTimeout,
                HEARTBEAT_INTERVAL,
                MAX_FAILED_HEARTBEATS,
                clientSSLEngineFactory,
                serverSSLEngineFactory,
                unicastListener,
                unicast,
                transportProtocolHandler,
                tcpProtocolHandler,
                transportStats,
                mock(Timer.class),
                mock(IRoundTripTimes.class));

        transportReader = new TransportReader(String.format("%s-%s", transportId, userID.getString()), outgoingEventSink, transportListener);
    }

    public void start(ServerSocketChannelFactory serverSocketChannelFactory, ClientSocketChannelFactory clientSocketChannelFactory)
            throws Exception
    {
        ServerBootstrap serverBootstrap = tcpBootstrapFactory.newServerBootstrap(serverSocketChannelFactory, serverChannelTeardownHandler);
        ClientBootstrap clientBootstrap = tcpBootstrapFactory.newClientBootstrap(clientSocketChannelFactory, clientChannelTeardownHandler);
        unicast.setBootstraps(serverBootstrap, clientBootstrap);

        unicast.start(listeningAddress);
        linkStateService.markLinksUp();
        transportReader.start();
    }
}

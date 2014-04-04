/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.zephyr;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.C;
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
import com.aerofs.daemon.transport.MockRockLog;
import com.aerofs.daemon.transport.PrivateKeyProvider;
import com.aerofs.daemon.transport.TransportReader;
import com.aerofs.daemon.transport.lib.PulseManager;
import com.aerofs.daemon.transport.lib.SemaphoreTriggeringListener;
import com.aerofs.daemon.transport.lib.StreamManager;
import com.aerofs.daemon.transport.lib.TransportStats;
import com.aerofs.daemon.transport.lib.UnicastProxy;
import com.aerofs.daemon.transport.lib.UnicastTransportListener;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler.ChannelMode;
import com.aerofs.daemon.transport.lib.handlers.TransportProtocolHandler;
import com.aerofs.daemon.transport.xmpp.XMPPConnectionService;
import com.aerofs.daemon.transport.xmpp.XMPPConnectionService.IXMPPConnectionServiceListener;
import com.aerofs.daemon.transport.xmpp.signalling.SignallingService;
import com.aerofs.lib.event.IEvent;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.util.HashedWheelTimer;
import org.jivesoftware.smack.XMPPConnection;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.SecureRandom;
import java.util.Random;
import java.util.concurrent.Semaphore;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public final class UnicastZephyrDevice
{
    public DID did = DID.generate();
    public LinkStateService linkStateService = new LinkStateService();
    public BlockingPrioQueue<IEvent> outgoingEventSink = new BlockingPrioQueue<IEvent>(100);
    public UserID userID;
    public SemaphoreTriggeringListener unicastListener;
    public UnicastTransportListener transportListener;
    public TransportReader transportReader;
    public ZephyrConnectionService unicast;
    public XMPPConnectionService xmppConnectionService;

    public UnicastZephyrDevice(Random random, SecureRandom secureRandom, String zephyrHost, int zephyrPort, MockCA mockCA, MockRockLog mockRockLog, UnicastTransportListener transportListener)
            throws Exception
    {
        this.transportListener = transportListener;

        String transportId = String.format("t-%d", random.nextInt(10));
        ITransport transport = mock(ITransport.class);
        when(transport.id()).thenReturn(transportId);

        unicastListener = spy(new SemaphoreTriggeringListener());

        userID = UserID.fromExternal(String.format("user%d@arrowfs.org", random.nextInt(10)));

        xmppConnectionService = new XMPPConnectionService("z", did, new InetSocketAddress("localhost", 5222), "arrowfs.org", "s", new byte[]{0}, 1000, 2, 1000, 5000, mockRockLog.getRockLog(), linkStateService);

        StreamManager streamManager = new StreamManager();
        PulseManager pulseManager = new PulseManager();
        TransportStats transportStats = new TransportStats();

        SignallingService signallingService = new SignallingService("z", "arrowfs.org", xmppConnectionService);
        UnicastProxy workaround = new UnicastProxy();
        IPrivateKeyProvider privateKeyProvider = new PrivateKeyProvider(secureRandom, BaseSecUtil.getCertificateCName(userID, did), mockCA.getCaName(), mockCA.getCACertificateProvider().getCert(), mockCA.getCaKeyPair().getPrivate());
        SSLEngineFactory clientSSLEngineFactory = new SSLEngineFactory(Mode.Client, Platform.Desktop, privateKeyProvider, mockCA.getCACertificateProvider(), null);
        SSLEngineFactory serverSSLEngineFactory = new SSLEngineFactory(Mode.Server, Platform.Desktop, privateKeyProvider, mockCA.getCACertificateProvider(), null);
        TransportProtocolHandler transportProtocolHandler = new TransportProtocolHandler(transport, outgoingEventSink, streamManager, pulseManager);
        ChannelTeardownHandler twowayChannelTeardownHandler = new ChannelTeardownHandler(transport, outgoingEventSink, streamManager, ChannelMode.SERVER);

        transportReader = new TransportReader(String.format("%s-%s", transportId, userID.getString()), outgoingEventSink, transportListener);
        unicast = new ZephyrConnectionService(
                userID,
                did,
                10 * C.SEC,
                3,
                10 * C.SEC,
                clientSSLEngineFactory,
                serverSSLEngineFactory,
                unicastListener,
                linkStateService,
                signallingService,
                transportProtocolHandler,
                twowayChannelTeardownHandler,
                transportStats,
                new HashedWheelTimer(),
                mockRockLog.getRockLog(),
                new NioClientSocketChannelFactory(),
                new InetSocketAddress(zephyrHost, zephyrPort),
                Proxy.NO_PROXY);

        workaround.setRealUnicast(unicast);
    }

    public void init()
    {
        unicast.init();
    }

    public void start()
            throws Exception
    {
        final Semaphore xmppServerConnectedSemaphore = new Semaphore(0);

        xmppConnectionService.addListener(new IXMPPConnectionServiceListener()
        {
            @Override
            public void xmppServerConnected(XMPPConnection conn)
                    throws Exception
            {
                xmppServerConnectedSemaphore.release();
            }

            @Override
            public void xmppServerDisconnected()
            {
                // noop
            }
        });

        // mark all the network links as up
        linkStateService.markLinksUp();

        // start the XMPPConnectionService and block until it actually connects
        xmppConnectionService.start();
        xmppServerConnectedSemaphore.acquire();

        unicast.start();
        transportReader.start();
    }
}

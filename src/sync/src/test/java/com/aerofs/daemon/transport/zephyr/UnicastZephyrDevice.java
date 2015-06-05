/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.zephyr;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.C;
import com.aerofs.daemon.transport.*;
import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import com.aerofs.base.ssl.IPrivateKeyProvider;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.base.ssl.SSLEngineFactory.Mode;
import com.aerofs.base.ssl.SSLEngineFactory.Platform;
import com.aerofs.daemon.lib.BlockingPrioQueue;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.daemon.transport.lib.IRoundTripTimes;
import com.aerofs.daemon.transport.lib.SemaphoreTriggeringListener;
import com.aerofs.daemon.transport.lib.StreamManager;
import com.aerofs.daemon.transport.lib.TransportStats;
import com.aerofs.daemon.transport.lib.UnicastProxy;
import com.aerofs.daemon.transport.lib.UnicastTransportListener;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler;
import com.aerofs.daemon.transport.lib.handlers.ChannelTeardownHandler.ChannelMode;
import com.aerofs.daemon.transport.lib.handlers.TransportProtocolHandler;
import com.aerofs.lib.event.IEvent;
import org.jboss.netty.util.HashedWheelTimer;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.SecureRandom;
import java.util.concurrent.ThreadLocalRandom;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public final class UnicastZephyrDevice
{
    public DID did;
    public LinkStateService linkStateService = new LinkStateService();
    public BlockingPrioQueue<IEvent> outgoingEventSink = new BlockingPrioQueue<IEvent>(100);
    public UserID userID;
    public SemaphoreTriggeringListener unicastListener;
    public UnicastTransportListener transportListener;
    public TransportReader transportReader;
    public ZephyrConnectionService unicast;

    public UnicastZephyrDevice(
            DID did,
            SecureRandom secureRandom,
            String zephyrHost,
            int zephyrPort,
            MockCA mockCA,
            UnicastTransportListener transportListener,
            ISignallingService signallingService,
            IRoundTripTimes roundTripTimes)
            throws Exception
    {
        this.did = did;
        this.transportListener = transportListener;

        String transportId = String.format("t-%d", ThreadLocalRandom.current().nextInt(10));
        ITransport transport = mock(ITransport.class);
        when(transport.id()).thenReturn(transportId);

        unicastListener = spy(new SemaphoreTriggeringListener());

        userID = UserID.fromExternal(String.format("user%d@arrowfs.org",
                ThreadLocalRandom.current().nextInt(10)));

        StreamManager streamManager = new StreamManager(30 * C.SEC);
        TransportStats transportStats = new TransportStats();

        UnicastProxy workaround = new UnicastProxy();
        IPrivateKeyProvider privateKeyProvider = new PrivateKeyProvider(secureRandom, BaseSecUtil.getCertificateCName(userID, did), mockCA.getCaName(), mockCA.getCACertificateProvider().getCert(), mockCA.getCaKeyPair().getPrivate());
        SSLEngineFactory clientSSLEngineFactory = new SSLEngineFactory(Mode.Client, Platform.Desktop, privateKeyProvider, mockCA.getCACertificateProvider(), null);
        SSLEngineFactory serverSSLEngineFactory = new SSLEngineFactory(Mode.Server, Platform.Desktop, privateKeyProvider, mockCA.getCACertificateProvider(), null);
        TransportProtocolHandler transportProtocolHandler = new TransportProtocolHandler(transport, outgoingEventSink, streamManager);
        ChannelTeardownHandler twowayChannelTeardownHandler = new ChannelTeardownHandler(transport, streamManager, ChannelMode.SERVER);

        transportReader = new TransportReader(String.format("%s-%s", transportId, userID.getString()), outgoingEventSink, transportListener);
        unicast = new ZephyrConnectionService(
                userID,
                did,
                10 * C.SEC,
                3,
                10 * C.SEC,
                clientSSLEngineFactory,
                serverSSLEngineFactory,
                transport,
                unicastListener,
                unicastListener,
                linkStateService,
                signallingService,
                transportProtocolHandler,
                twowayChannelTeardownHandler,
                transportStats,
                new HashedWheelTimer(),
                ChannelFactories.newClientChannelFactory(),
                new InetSocketAddress(zephyrHost, zephyrPort),
                Proxy.NO_PROXY,
                roundTripTimes);

        workaround.setRealUnicast(unicast);
    }

    public void init()
    {
        unicast.init();
    }

    public void start()
            throws Exception
    {
        // mark all the network links as up
        linkStateService.markLinksUp();

        unicast.start();
        transportReader.start();
    }

    public void stop()
    {
        transportReader.stop();
        unicast.stop();
    }
}

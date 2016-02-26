/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.net.ClientSSLEngineFactory;
import com.aerofs.daemon.core.net.ServerSSLEngineFactory;
import com.aerofs.daemon.core.net.Transports;
import com.aerofs.daemon.transport.presence.LocationManager;
import com.aerofs.daemon.transport.ssmp.SSMPConnectionService;
import com.aerofs.daemon.transport.zephyr.ZephyrParams;
import com.aerofs.ids.DID;
import com.aerofs.ids.SID;
import com.aerofs.ids.UserID;
import com.aerofs.base.ssl.IPrivateKeyProvider;
import com.aerofs.daemon.core.net.TransportFactory.TransportType;
import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.daemon.event.net.tx.EOUnicastMessage;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.daemon.transport.lib.IRoundTripTimes;
import com.aerofs.lib.cfg.*;
import com.aerofs.lib.event.Prio;
import com.aerofs.ssmp.SSMPConnection;
import com.google.common.io.Files;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;

import java.io.File;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TransportResource extends ExternalResource
{
    private static final Logger l = Loggers.getLogger(TransportResource.class);

    private final long seed = System.nanoTime();
    private final Random random = new Random(seed);
    private final LinkStateService linkStateService = new LinkStateService();
    private final SecureRandom secureRandom = new SecureRandom();
    private final Timer timer = new HashedWheelTimer();
    private final CoreQueue outgoingEventSink = new CoreQueue();
    private final ClientSocketChannelFactory clientSocketChannelFactory = ChannelFactories.newClientChannelFactory();
    private final ServerSocketChannelFactory serverSocketChannelFactory = ChannelFactories.newServerChannelFactory();
    private final TransportType transportType;
    private final String transportId;
    private final MockCA mockCA;
    private final InetSocketAddress ssmpAddress;
    private final InetSocketAddress zephyrAddress;
    private final IRoundTripTimes roundTripTimes = mock(IRoundTripTimes.class);

    private DID did;
    private Transports tps;
    private ITransport transport;
    private File logFilePath;
    private TransportReader transportReader;
    private boolean readerSet;

    public TransportResource(TransportType transportType, MockCA mockCA,
            InetSocketAddress ssmpAddress, InetSocketAddress zephyrAddress)
    {
        l.info("seed:{}", seed);

        secureRandom.setSeed(random.nextLong());

        this.transportType = transportType;
        this.transportId = String.format("%s-%d", this.transportType.getId(), Math.abs(random.nextInt()));
        this.mockCA = mockCA;
        this.ssmpAddress = ssmpAddress;
        this.zephyrAddress = zephyrAddress;
    }

    @Override
    protected void before()
            throws Throwable
    {
        super.before();

        logFilePath = Files.createTempDir();
        checkState(logFilePath.exists());

        l.info("transport test log directory:{}", logFilePath.getAbsolutePath());

        UserID userID = UserID.fromExternal(String.format("testuser+%d@arrowfs.org", Math.abs(random.nextInt())));
        checkNotNull(userID);

        did = DID.generate();
        checkNotNull(did);

        IPrivateKeyProvider privateKeyProvider = new PrivateKeyProvider(secureRandom, BaseSecUtil.getCertificateCName(userID, did), mockCA.getCaName(), mockCA.getCACertificateProvider().getCert(), mockCA.getCaKeyPair().getPrivate());

        CfgLocalUser localid = mock(CfgLocalUser.class);
        when(localid.get()).thenReturn(userID);
        CfgLocalDID localdid = mock(CfgLocalDID.class);
        when(localdid.get()).thenReturn(did);
        CfgTimeout timeout = mock(CfgTimeout.class);
        when(timeout.get()).thenReturn(15 * C.SEC);
        CfgMulticastLoopback multicastLoopback = mock(CfgMulticastLoopback.class);
        when(multicastLoopback.get()).thenReturn(true);
        CfgKeyManagersProvider keyProvider = mock(CfgKeyManagersProvider.class);
        when(keyProvider.getCert()).thenReturn(privateKeyProvider.getCert());
        when(keyProvider.getPrivateKey()).thenReturn(privateKeyProvider.getPrivateKey());
        CfgCACertificateProvider trustedCA = mock(CfgCACertificateProvider.class);
        when(trustedCA.getCert()).thenReturn(mockCA.getCACertificateProvider().getCert());

        CfgEnabledTransports enabled = mock(CfgEnabledTransports.class);
        when(enabled.isZephyrEnabled()).thenReturn(transportType == TransportType.ZEPHYR);
        when(enabled.isTcpEnabled()).thenReturn(transportType == TransportType.LANTCP);

        ClientSSLEngineFactory clientSslEngineFactory = new ClientSSLEngineFactory(keyProvider, trustedCA);

        SSMPConnection ssmp = new SSMPConnection(localdid.get(), ssmpAddress, timer,
                clientSocketChannelFactory, clientSslEngineFactory::newSslHandler);

        tps = new Transports(localid, localdid, enabled, timeout,
                new ZephyrParams(zephyrAddress),
                timer, outgoingEventSink,
                linkStateService,
                clientSslEngineFactory,
                new ServerSSLEngineFactory(keyProvider, trustedCA),
                clientSocketChannelFactory, serverSocketChannelFactory,
                new SSMPConnectionService(outgoingEventSink, linkStateService, ssmp, localdid),
                new LocationManager(ssmp),
                roundTripTimes);

        checkState(tps.getAll().size() == 1);
        tps.init_();
        tps.start_();

        transport = tps.getAll().iterator().next();
        linkStateService.markLinksUp();
    }

    @Override
    protected synchronized void after()
    {
        tps.stop_();

        if (readerSet) {
            transportReader.stop();
        }

        if (logFilePath != null) {
            // noinspection ResultOfMethodCallIgnored
            logFilePath.delete();
        }

        clientSocketChannelFactory.shutdown();
        clientSocketChannelFactory.releaseExternalResources();

        serverSocketChannelFactory.shutdown();
        serverSocketChannelFactory.releaseExternalResources();

        linkStateService.markLinksDown();

        timer.stop();
    }

    //
    // listeners to be notified on incoming events
    //

    public synchronized void setTransportListener(TransportListener transportListener)
    {
        if (readerSet) {
            return;
        }

        // doesn't matter if this is started after transport - events don't disappear :)
        transportReader = new TransportReader(transportId + "-q-reader", outgoingEventSink, transportListener);
        transportReader.start();
        readerSet = true;
    }

    //
    // getters
    //

    public DID getDID()
    {
        return checkNotNull(did);
    }

    public ITransport getTransport()
    {
        return checkNotNull(transport);
    }

    public byte[] getRandomBytes(int numBytes)
    {
        synchronized (random) {
            byte[] randomBytes = new byte[numBytes];
            random.nextBytes(randomBytes);
            return randomBytes;
        }
    }

    //
    // API calls
    //

    public void joinStore(SID sid)
    {
        tps.presenceSources().forEach(ps -> ps.updateInterest(sid, true));
    }

    public void send(DID remoteDID, byte[] payload, Prio prio)
    {
        getTransport().q().enqueueBlocking(new EOUnicastMessage(remoteDID, payload), prio);
    }

    public void sendBlocking(DID remoteDID, byte[] payload, Prio prio)
            throws Exception
    {
        final AtomicReference<Exception> sendExceptionReference = new AtomicReference<>(null);
        final Semaphore sendSemaphore = new Semaphore(0);

        // we actually want to be notified when the packet was sent
        EOUnicastMessage unicastMessage = new EOUnicastMessage(remoteDID, payload);
        unicastMessage.setWaiter(new IResultWaiter()
        {
            @Override
            public void okay()
            {
                l.debug("sent from {} to {}", did, remoteDID);
                sendSemaphore.release();
            }

            @Override
            public void error(Exception e)
            {
                l.debug("failed to send from {} to {}", did, remoteDID);
                sendExceptionReference.set(e);
                sendSemaphore.release();
            }
        });

        getTransport().q().enqueueBlocking(unicastMessage, prio);

        // wait until the packet was sent
        sendSemaphore.acquire();

        // if there was an exception in sending the packet let us know
        Exception exception = sendExceptionReference.get();
        if (exception != null) {
            throw exception;
        }
    }

    public void pauseSyncing()
    {
        linkStateService.markLinksDown();
    }

    public void resumeSyncing()
    {
        linkStateService.markLinksUp();
    }

    public OutputStream newOutgoingStream(DID did)
    {
        return new TransportOutputStream(did, transport);
    }
}

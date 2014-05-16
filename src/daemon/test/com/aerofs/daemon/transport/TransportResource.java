/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport;

import com.aerofs.base.BaseSecUtil;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.ssl.ICertificateProvider;
import com.aerofs.base.ssl.IPrivateKeyProvider;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.base.ssl.SSLEngineFactory.Mode;
import com.aerofs.base.ssl.SSLEngineFactory.Platform;
import com.aerofs.daemon.core.net.TransferStatisticsManager;
import com.aerofs.daemon.core.net.TransportFactory;
import com.aerofs.daemon.core.net.TransportFactory.TransportType;
import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.daemon.event.net.EOUpdateStores;
import com.aerofs.daemon.event.net.tx.EOUnicastMessage;
import com.aerofs.daemon.lib.BlockingPrioQueue;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import com.google.common.io.Files;
import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;

import java.io.File;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.SecureRandom;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.mockito.Mockito.mock;

public final class TransportResource extends ExternalResource
{
    static
    {
        System.loadLibrary("aerofsd");
    }

    private static final Logger l = Loggers.getLogger(TransportResource.class);

    private static final Prio PRIO = Prio.LO;

    private final long seed = System.nanoTime();
    private final Random random = new Random(seed);
    private final LinkStateService linkStateService = new LinkStateService();
    private final SecureRandom secureRandom = new SecureRandom();
    private final Timer timer = new HashedWheelTimer();
    private final BlockingPrioQueue<IEvent> outgoingEventSink = new BlockingPrioQueue<IEvent>(DaemonParam.QUEUE_LENGTH_DEFAULT);
    private final ClientSocketChannelFactory clientSocketChannelFactory = ChannelFactories.newClientChannelFactory();
    private final ServerSocketChannelFactory serverSocketChannelFactory = ChannelFactories.newServerChannelFactory();
    private final TransferStatisticsManager transferStatisticsManager = mock(TransferStatisticsManager.class);
    private final TransportType transportType;
    private final String transportId;
    private final MockCA mockCA;
    private final MockRockLog mockRockLog;
    private final InetSocketAddress zephyrAddress;
    private final InetSocketAddress xrayAddress;

    private DID did;
    private ITransport transport;
    private File logFilePath;
    private TransportReader transportReader;
    private boolean readerSet;

    public TransportResource(TransportType transportType, MockCA mockCA, MockRockLog mockRockLog, InetSocketAddress zephyrAddress, InetSocketAddress xrayAddress)
    {
        l.info("seed:{}", seed);

        secureRandom.setSeed(random.nextLong());

        this.transportType = transportType;
        this.transportId = String.format("%s-%d", this.transportType.getId(), Math.abs(random.nextInt()));
        this.mockCA = mockCA;
        this.mockRockLog = mockRockLog;
        this.zephyrAddress = zephyrAddress;
        this.xrayAddress = xrayAddress;
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

        byte[] scrypted = new byte[]{0};
        checkNotNull(scrypted);

        IPrivateKeyProvider privateKeyProvider = new PrivateKeyProvider(secureRandom, BaseSecUtil.getCertificateCName(userID, did), mockCA.getCaName(), mockCA.getCACertificateProvider().getCert(), mockCA.getCaKeyPair().getPrivate());
        SSLEngineFactory clientSSLEngineFactory = newClientSSLEngineFactory(privateKeyProvider, mockCA.getCACertificateProvider());
        SSLEngineFactory serverSSLEngineFactory = newServerSSLEngineFactory(privateKeyProvider, mockCA.getCACertificateProvider());

        // [sigh] It's stupid to have to create this every time. I think it should be injected in
        // too bad JUnit doesn't allow nested @Rule definitions
        TransportFactory transportFactory = new TransportFactory(
                logFilePath.getAbsolutePath(),
                userID,
                did,
                scrypted,
                true,
                true,
                InetSocketAddress.createUnresolved("localhost", 3478),
                InetSocketAddress.createUnresolved("localhost", 5222),
                "arrowfs.org",
                2 * C.SEC,
                2,
                1 * C.SEC,
                5 * C.SEC,
                10 * C.SEC,
                90 * C.SEC,
                3,
                10 * C.SEC,
                zephyrAddress,
                10 * C.SEC,
                xrayAddress,
                Proxy.NO_PROXY,
                timer,
                outgoingEventSink,
                mockRockLog.getRockLog(),
                linkStateService,
                new MaxcastFilterReceiver(),
                null,
                clientSocketChannelFactory,
                serverSocketChannelFactory,
                clientSSLEngineFactory,
                serverSSLEngineFactory
        );

        transport = transportFactory.newTransport(transportType, transportId, 1);
        transport.init();
        transport.start();
        linkStateService.markLinksUp();
    }

    private SSLEngineFactory newServerSSLEngineFactory(IPrivateKeyProvider privateKeyProvider, ICertificateProvider caCertificateProvider)
    {
        return new SSLEngineFactory(Mode.Server, Platform.Desktop, privateKeyProvider, caCertificateProvider, null);
    }

    private SSLEngineFactory newClientSSLEngineFactory(IPrivateKeyProvider privateKeyProvider, ICertificateProvider caCertificateProvider)
    {
        return new SSLEngineFactory(Mode.Client, Platform.Desktop, privateKeyProvider, caCertificateProvider, null);
    }

    @Override
    protected synchronized void after()
    {
        transport.stop();

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
        getTransport().q().enqueueBlocking(new EOUpdateStores(new FakeIMCExecutor(), new SID[]{sid}, new SID[]{}), PRIO);
    }

    public void send(DID remoteDID, byte[] payload, Prio prio)
    {
        getTransport().q().enqueueBlocking(new EOUnicastMessage(remoteDID, payload), prio);
    }

    public void sendBlocking(DID remoteDID, byte[] payload, Prio prio)
            throws Exception
    {
        final AtomicReference<Exception> sendExceptionReference = new AtomicReference<Exception>(null);
        final Semaphore sendSemaphore = new Semaphore(0);

        // we actually want to be notified when the packet was sent
        EOUnicastMessage unicastMessage = new EOUnicastMessage(remoteDID, payload);
        unicastMessage.setWaiter(new IResultWaiter()
        {
            @Override
            public void okay()
            {
                sendSemaphore.release();
            }

            @Override
            public void error(Exception e)
            {
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
        StreamID streamID;

        synchronized (random) {
            streamID = new StreamID(random.nextInt());
        }

        return new TransportOutputStream(did, streamID, transport, transport.q(), new FakeIMCExecutor(), transferStatisticsManager);
    }
}

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
import com.aerofs.daemon.core.net.TransportFactory;
import com.aerofs.daemon.core.net.TransportFactory.TransportType;
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
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;

import java.io.File;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.SecureRandom;
import java.util.Random;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.concurrent.Executors.newCachedThreadPool;

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
    private final SecureRandom secureRandom = new SecureRandom();
    private final BlockingPrioQueue<IEvent> outgoingEventSink = new BlockingPrioQueue<IEvent>(DaemonParam.QUEUE_LENGTH_DEFAULT);
    private final ClientSocketChannelFactory clientSocketChannelFactory = new NioClientSocketChannelFactory(newCachedThreadPool(), newCachedThreadPool(), 2, 2);
    private final ServerSocketChannelFactory serverSocketChannelFactory = new NioServerSocketChannelFactory(newCachedThreadPool(), newCachedThreadPool(), 2);
    private final TransportType transportType;
    private final String transportId;
    private final LinkStateService linkStateService;
    private final MockCA mockCA;
    private final MockRockLog mockRockLog;

    private DID did;
    private ITransport transport;
    private File logFilePath;
    private TransportReader transportReader;
    private boolean readerSet;

    public TransportResource(TransportType transportType, LinkStateService linkStateService, MockCA mockCA, MockRockLog mockRockLog)
    {
        l.info("seed:{}", seed);

        secureRandom.setSeed(random.nextLong());

        this.transportType = transportType;
        this.transportId = String.format("%s-%d", this.transportType.getId(), Math.abs(random.nextInt()));
        this.linkStateService = linkStateService;
        this.mockCA = mockCA;
        this.mockRockLog = mockRockLog;
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
                InetSocketAddress.createUnresolved("zephyr.aerofs.com", 443),
                Proxy.NO_PROXY,
                outgoingEventSink,
                mockRockLog.getRockLog(),
                linkStateService,
                new MaxcastFilterReceiver(),
                null, clientSocketChannelFactory, serverSocketChannelFactory,
                clientSSLEngineFactory,
                serverSSLEngineFactory
        );

        transport = transportFactory.newTransport(transportType, transportId, 1);
        transport.init();
        transport.start();
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

    public OutputStream newOutgoingStream(DID did)
    {
        StreamID streamID;

        synchronized (random) {
            streamID = new StreamID(random.nextInt());
        }

        return new TransportOutputStream(did, streamID, transport.q(), new FakeIMCExecutor());
    }
}

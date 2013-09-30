/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport;

import com.aerofs.base.BaseSecUtil;
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
import com.aerofs.daemon.core.net.link.ILinkStateListener;
import com.aerofs.daemon.core.net.link.LinkStateService;
import com.aerofs.daemon.event.net.EIPresence;
import com.aerofs.daemon.event.net.EOLinkStateChanged;
import com.aerofs.daemon.event.net.EOUpdateStores;
import com.aerofs.daemon.event.net.rx.EIUnicastMessage;
import com.aerofs.daemon.event.net.tx.EOUnicastMessage;
import com.aerofs.daemon.lib.BlockingPrioQueue;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import com.aerofs.rocklog.RockLog;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Proxy;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;

public final class TransportResource extends ExternalResource
{
    private static final Logger l = Loggers.getLogger(TransportResource.class);

    private static final int TEST_SEED = 72;
    private static final int SINK_CAPACITY = 100;
    private static final Prio PRIO = Prio.LO;

    private final Random _random = new Random(TEST_SEED);
    private final SecureRandom _secureRandom = new SecureRandom();
    private final BlockingPrioQueue<IEvent> _eventSink = new BlockingPrioQueue<IEvent>(SINK_CAPACITY);
    private final TransportType _transportType;
    private final String _transportId;
    private final String _caName;
    private final LinkStateService _linkStateService;
    private final RockLog _rockLog;
    private final KeyPair _caKeyPair;
    private final AtomicBoolean _running = new AtomicBoolean(false);

    private TransportListener _transportListener;
    private UserID _userID;
    private DID _did;
    private byte[] _scrypted;
    private ITransport _transport;
    private File _logFilePath;
    private Thread _eventQueueReader;

    public TransportResource(TransportType transportType, LinkStateService linkStateService, RockLog rockLog, KeyPair caKeyPair)
    {
        _secureRandom.setSeed(_random.nextLong());

        _transportType = transportType;
        _transportId = String.format("%s-%d", _transportType.getId(), Math.abs(_random.nextInt()));
        _caName = String.format("testca-%d@arrowfs.org", Math.abs(_random.nextInt()));
        _linkStateService = linkStateService;
        _rockLog = rockLog;
        _caKeyPair = caKeyPair;
    }

    @Override
    protected void before()
            throws Throwable
    {
        super.before();

        _logFilePath = Files.createTempDir();
        checkState(_logFilePath.exists());

        l.info("transport test log directory:{}", _logFilePath.getAbsolutePath());

        _userID = UserID.fromExternal(String.format("testuser-%d@arrowfs.org", _random.nextInt()));
        checkNotNull(_userID);

        _did = DID.generate();
        checkNotNull(_did);

        _scrypted = new byte[]{0};
        checkNotNull(_scrypted);

        ClientSocketChannelFactory clientSocketChannelFactory = new NioClientSocketChannelFactory();
        ServerSocketChannelFactory serverSocketChannelFactory = new NioServerSocketChannelFactory();

        ICertificateProvider caCertificateProvider = new CACertificateProvider(_secureRandom, _caName, _caKeyPair);
        IPrivateKeyProvider privateKeyProvider = new PrivateKeyProvider(_secureRandom, BaseSecUtil.getCertificateCName(_userID, _did), _caName, caCertificateProvider.getCert(), _caKeyPair.getPrivate());

        SSLEngineFactory clientSSLEngineFactory = newClientSSLEngineFactory(privateKeyProvider, caCertificateProvider);
        SSLEngineFactory serverSSLEngineFactory = newServerSSLEngineFactory(privateKeyProvider, caCertificateProvider);

        // [sigh] It's stupid to have to create this every time. I think it should be injected in
        // too bad JUnit doesn't allow nested @Rule definitions
        TransportFactory transportFactory = new TransportFactory(
                _logFilePath.getAbsolutePath(),
                _userID,
                _did,
                _scrypted,
                true,
                true,
                InetSocketAddress.createUnresolved("localhost", 3478),
                InetSocketAddress.createUnresolved("localhost", 5222),
                "arrowfs.org",
                InetSocketAddress.createUnresolved("relay.aerofs.com", 443),
                Proxy.NO_PROXY,
                _eventSink,
                _rockLog,
                new MaxcastFilterReceiver(),
                null,
                clientSocketChannelFactory,
                serverSocketChannelFactory,
                clientSSLEngineFactory,
                serverSSLEngineFactory
        );

        _transport = transportFactory.newTransport(_transportType);
        checkNotNull(_transport, _transportId, 1);

        _linkStateService.addListener_(new ILinkStateListener()
        {
            @Override
            public void onLinkStateChanged_(ImmutableSet<NetworkInterface> previous, ImmutableSet<NetworkInterface> current, ImmutableSet<NetworkInterface> added, ImmutableSet<NetworkInterface> removed)
            {
                _transport.q().enqueueBlocking(new EOLinkStateChanged(new FakeIMCExecutor(), previous, current, added, removed), PRIO);
            }
        }, sameThreadExecutor());

        _transport.init_();

        _transport.start_();

        _running.set(true);

        createAndStartIncomingTransportEventReader(); // doesn't matter if this is started after transport - events don't disappear :)
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
    protected void after()
    {
        try {
            _running.set(false);

            if (_logFilePath != null) {
                // noinspection ResultOfMethodCallIgnored
                _logFilePath.delete();
            }

            _eventQueueReader.join();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    //
    // listeners to be notified on incoming events
    //

    public void setTransportListener(TransportListener transportListener)
    {
        _transportListener = transportListener;
    }

    //
    // getters
    //

    public DID getDID()
    {
        return checkNotNull(_did);
    }

    public ITransport getTransport()
    {
        return checkNotNull(_transport);
    }

    //
    // API calls
    //

    public void joinStore(SID sid)
    {
        getTransport().q().enqueueBlocking(new EOUpdateStores(new FakeIMCExecutor(), new SID[]{sid}, new SID[]{}), PRIO);
    }

    public void leaveStore(SID sid)
    {
        getTransport().q().enqueueBlocking(new EOUpdateStores(new FakeIMCExecutor(), new SID[]{}, new SID[]{sid}), PRIO);
    }

    public void send(DID remoteDID, byte[] payload, Prio prio)
    {
        getTransport().q().enqueueBlocking(new EOUnicastMessage(remoteDID, payload), prio);
    }

    //
    // internal calls
    //

    private void createAndStartIncomingTransportEventReader()
    {
        _eventQueueReader = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                while (_running.get()) {
                    try {
                        OutArg<Prio> outArg = new OutArg<Prio>(null);
                        IEvent event = _eventSink.tryDequeue(outArg);

                        if (event == null) {
                            Thread.sleep(100);
                            continue;
                        }

                        l.info("handling event type:{}", event.getClass().getSimpleName());

                        if (event instanceof EIPresence) {
                            handlePresence((EIPresence) event);
                        } else if (event instanceof EIUnicastMessage) {
                            handleUnicastMessage((EIUnicastMessage)event);
                        } else {
                            l.warn("ignore transport event of type:{}", event.getClass().getSimpleName());
                        }
                    } catch (InterruptedException e) {
                        // ignore - loop and see if we were interrupted for a reason
                    } catch (Throwable t) {
                        throw new IllegalStateException("incoming transport event reader caught err", t);
                    }
                }
            }
        });

        _eventQueueReader.setName(_transportId + "-q-reader");
        _eventQueueReader.start();
    }

    private void handlePresence(EIPresence presence)
    {
        for (Map.Entry<DID, Collection<SID>> entry : presence._did2sids.entrySet()) {
            if (presence._online) {
                _transportListener.onDeviceAvailable(entry.getKey(), entry.getValue());
            } else {
                _transportListener.onDeviceUnavailable(entry.getKey(), entry.getValue());
            }
        }
    }

    private void handleUnicastMessage(EIUnicastMessage event)
            throws IOException
    {
        int numBytes = event.is().available();
        byte[] packet = new byte[numBytes];

        // noinspection ResultOfMethodCallIgnored
        event.is().read(packet);

        _transportListener.onIncomingPacket(event._ep.did(), event._userID, packet);
    }
}

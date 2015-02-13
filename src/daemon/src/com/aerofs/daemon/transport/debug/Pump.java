/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.debug;

import com.aerofs.base.BaseParam;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.TimerUtil;
import com.aerofs.ids.DID;
import com.aerofs.ids.SID;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.core.net.ChunksCounter;
import com.aerofs.daemon.core.net.TransportFactory;
import com.aerofs.daemon.core.net.TransportFactory.ExUnsupportedTransport;
import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.daemon.event.net.EIStoreAvailability;
import com.aerofs.daemon.event.net.EOUpdateStores;
import com.aerofs.daemon.event.net.rx.EIUnicastMessage;
import com.aerofs.daemon.event.net.tx.EOUnicastMessage;
import com.aerofs.daemon.lib.BlockingPrioQueue;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.IRoundTripTimes;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.daemon.transport.lib.RoundTripTimes;
import com.aerofs.daemon.transport.zephyr.Zephyr;
import com.aerofs.lib.IProgram;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgAbsRTRoot;
import com.aerofs.lib.cfg.CfgCACertificateProvider;
import com.aerofs.lib.cfg.CfgKeyManagersProvider;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.cfg.CfgLolol;
import com.aerofs.lib.cfg.CfgScrypted;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.net.Proxy;
import java.util.Arrays;

import static com.aerofs.base.ssl.SSLEngineFactory.Mode.Client;
import static com.aerofs.base.ssl.SSLEngineFactory.Mode.Server;
import static com.aerofs.base.ssl.SSLEngineFactory.Platform.Desktop;
import static com.aerofs.daemon.core.net.TransportFactory.TransportType.JINGLE;
import static com.aerofs.daemon.core.net.TransportFactory.TransportType.LANTCP;
import static com.aerofs.daemon.core.net.TransportFactory.TransportType.ZEPHYR;
import static com.aerofs.lib.NioChannelFactories.getClientChannelFactory;
import static com.aerofs.lib.NioChannelFactories.getServerChannelFactory;
import static com.aerofs.lib.event.Prio.LO;
import static com.google.common.base.Preconditions.checkArgument;

public final class Pump implements IProgram
{
    private static final Logger l = Loggers.getLogger(Pump.class);
    private static final byte[] CHUNK = new byte[10 * C.KB];

    private final BlockingPrioQueue<IEvent> incomingEventSink = new BlockingPrioQueue<IEvent>(1024);
    private final LinkStateService linkStateService = new LinkStateService();
    private final SendThread sendThread = new SendThread();

    private final Object doSendLock = new Object();
    private ThroughputCounter throughputCounter;
    private volatile boolean doSend;
    private boolean isSender;
    private ITransport transport;
    private @Nullable DID remote;

    @Override
    public void launch_(String rtRoot, String prog, String[] args) // PROG RTROOT [t|z|j] [send|recv] <did>
            throws Exception
    {
        Util.initDriver("pp");

        l.info(Arrays.toString(args));
        checkArgument(args.length == 2 || args.length == 3, String.format("usage: SEND:(%s [t|z|j] send [did]) RECV:(%s [t|z|j] recv)", prog, prog));

        isSender = args[1].equalsIgnoreCase("send");
        if (isSender) {
            throughputCounter = new ThroughputCounter("send");
            checkArgument(args.length == 3, String.format("usage: SEND:(%s [t|z|j] send [did])", prog));
            sendThread.start();
        } else {
            throughputCounter = new ThroughputCounter("recv");
        }

        remote = (isSender ? new DID(DID.fromStringFormal(args[2])) : null);
        transport = newTransport(args[0]);

        // start transport
        transport.init();
        transport.start();
        linkStateService.start();

        // join the root store, so that I can actually receive presence info

        transport.q().enqueueBlocking(new EOUpdateStores(new SID[]{Cfg.rootSID()}, new SID[]{}), LO);

        // start listening

        OutArg<Prio> eventPriority = new OutArg<Prio>(LO);
        //noinspection InfiniteLoopStatement
        while (true) {
            IEvent incoming = incomingEventSink.dequeue(eventPriority);
            if (incoming instanceof EIUnicastMessage) {
                handleUnicastMessage((EIUnicastMessage)incoming);
            } else if (incoming instanceof EIStoreAvailability) {
                handlePresence((EIStoreAvailability) incoming);
            } else {
                handleOther(incoming);
            }
        }
    }

    private ITransport newTransport(String transportId)
            throws ExUnsupportedTransport
    {
        TransportFactory transportFactory = newTransportFactory();

        if (transportId.equalsIgnoreCase("t")) {
            return transportFactory.newTransport(LANTCP);
        } else if (transportId.equalsIgnoreCase("z")) {
            Zephyr zephyr = (Zephyr) transportFactory.newTransport(ZEPHYR);
            zephyr.enableMulticast();
            return zephyr;
        } else if (transportId.equalsIgnoreCase("j")) {
            return transportFactory.newTransport(JINGLE);
        } else {
            throw new ExUnsupportedTransport(transportId);
        }
    }

    private TransportFactory newTransportFactory()
    {
        CfgAbsRTRoot absRTRoot = new CfgAbsRTRoot();
        CfgLocalUser localid = new CfgLocalUser();
        CfgLocalDID localdid = new CfgLocalDID();
        CfgScrypted scrypted = new CfgScrypted();
        CfgLolol lolol = new CfgLolol();
        Timer timer = TimerUtil.getGlobalTimer();
        MaxcastFilterReceiver maxcastFilterReceiver = new MaxcastFilterReceiver();
        CfgKeyManagersProvider keyProvider = new CfgKeyManagersProvider();
        CfgCACertificateProvider trustedCA = new CfgCACertificateProvider();
        SSLEngineFactory clientSslEngineFactory = new SSLEngineFactory(Client, Desktop, keyProvider, trustedCA, null);
        SSLEngineFactory serverSslEngineFactory = new SSLEngineFactory(Server, Desktop, keyProvider, trustedCA, null);
        ClientSocketChannelFactory clientChannelFactory = getClientChannelFactory();
        ServerSocketChannelFactory serverChannelFactory = getServerChannelFactory();
        IRoundTripTimes roundTripTimes = new RoundTripTimes();
        return new TransportFactory(
                absRTRoot.get(),
                localid.get(),
                localdid.get(),
                scrypted.get(),
                false,
                lolol.get(),
                DaemonParam.Jingle.STUN_SERVER_ADDRESS,
                BaseParam.XMPP.SERVER_ADDRESS,
                BaseParam.XMPP.getServerDomain(),
                5 * C.SEC,
                3,
                LibParam.EXP_RETRY_MIN_DEFAULT,
                LibParam.EXP_RETRY_MAX_DEFAULT,
                DaemonParam.DEFAULT_CONNECT_TIMEOUT,
                DaemonParam.HEARTBEAT_INTERVAL,
                DaemonParam.MAX_FAILED_HEARTBEATS,
                DaemonParam.Zephyr.HANDSHAKE_TIMEOUT,
                BaseParam.Zephyr.SERVER_ADDRESS,
                Proxy.NO_PROXY,
                timer,
                incomingEventSink,
                linkStateService,
                maxcastFilterReceiver,
                clientChannelFactory,
                serverChannelFactory,
                clientSslEngineFactory,
                serverSslEngineFactory,
                roundTripTimes);
    }

    private void handleUnicastMessage(EIUnicastMessage unicastMessage)
    {
        throughputCounter.observe(unicastMessage.wireLength());
        l.debug("recv incoming d:{}", unicastMessage._ep.did());
    }

    private void handlePresence(EIStoreAvailability presence)
    {
        if (presence._online && presence._did2sids.containsKey(remote)) {
            l.info("device reachable d:{}", remote);

            if (isSender) {
                synchronized (doSendLock) {
                    doSend = true;
                    doSendLock.notify();
                }
            }

        } else if (!presence._online && presence._did2sids.containsKey(remote)) {
            l.info("device unreachable d:{}", remote);

            if (isSender) {
                doSend = false;
            }
        }
    }

    private void handleOther(IEvent incoming)
    {
        l.warn("ignore event:{}", incoming.getClass().getSimpleName());
    }

    private class SendThread extends Thread
    {
        private final ChunksCounter chunksCounter = new ChunksCounter();

        @Override
        public void run()
        {
            //noinspection InfiniteLoopStatement
            while (true) {
                if (doSend) {
                    chunksCounter.waitIfTooManyChunks();
                    chunksCounter.incChunkCount();
                    EOUnicastMessage ev = new EOUnicastMessage(remote, CHUNK);
                    ev.setWaiter(waiter);
                    transport.q().enqueueBlocking(ev, LO);
                    throughputCounter.observe(ev.byteArray().length);
                } else {
                    synchronized (doSendLock) {
                        if (!doSend) {
                            try {
                                doSendLock.wait();
                                l.info("start send thd d:{}", remote);
                            } catch (InterruptedException e) {
                                // ignored
                            }
                        }
                    }
                }
            }
        }

        private final IResultWaiter waiter = new IResultWaiter()
        {
            @Override
            public void okay()
            {
                chunksCounter.decChunkCount();
            }

            @Override
            public void error(Exception e)
            {
                chunksCounter.decChunkCount();
            }
        };
    }
}

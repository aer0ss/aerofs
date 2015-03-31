/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.debug;

import com.aerofs.base.BaseParam;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.TimerUtil;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.daemon.core.CoreEventDispatcher;
import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.net.*;
import com.aerofs.daemon.core.net.OutgoingStreams.OutgoingStream;
import com.aerofs.daemon.core.net.IncomingStreams.StreamKey;
import com.aerofs.daemon.core.net.throttling.GlobalLimiter;
import com.aerofs.daemon.core.net.throttling.LimitMonitor;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.event.net.rx.EIChunk;
import com.aerofs.daemon.event.net.rx.EIStreamAborted;
import com.aerofs.daemon.event.net.rx.EIStreamBegun;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.defects.Defects;
import com.aerofs.ids.DID;
import com.aerofs.ids.SID;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.core.net.TransportFactory.ExUnsupportedTransport;
import com.aerofs.daemon.event.net.EIStoreAvailability;
import com.aerofs.daemon.event.net.EOUpdateStores;
import com.aerofs.daemon.event.net.rx.EIUnicastMessage;
import com.aerofs.daemon.event.net.tx.EOUnicastMessage;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.IRoundTripTimes;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.daemon.transport.lib.RoundTripTimes;
import com.aerofs.daemon.transport.zephyr.Zephyr;
import com.aerofs.lib.*;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgAbsRTRoot;
import com.aerofs.lib.cfg.CfgCACertificateProvider;
import com.aerofs.lib.cfg.CfgKeyManagersProvider;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.cfg.CfgLolol;
import com.aerofs.lib.cfg.CfgScrypted;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.ServerSocketChannelFactory;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;

import java.io.InputStream;
import java.net.Proxy;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

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
import static com.google.common.base.Preconditions.checkState;

public final class Pump implements IProgram, IUnicastInputLayer
{
    private static final Logger l = Loggers.getLogger(Pump.class);
    private static final byte[] CHUNK = new byte[10 * C.KB];

    private final LinkStateService linkStateService = new LinkStateService();
    private List<Producer> producers = new LinkedList<>();

    private ThroughputCounter sendThroughputCounter = new ThroughputCounter("send");
    private ThroughputCounter recvThroughputCounter = new ThroughputCounter("recv");

    private ITransport transport;

    // replicate core event handling
    private final UnicastInputOutputStack stack = new UnicastInputOutputStack();
    private IncomingStreams iss;
    private OutgoingStreams oss;

    private final CoreQueue queue = new CoreQueue();
    private final CoreScheduler sched = new CoreScheduler(queue);
    private final TokenManager tokenManager = new TokenManager(queue);
    private final CoreEventDispatcher disp = new CoreEventDispatcher(ImmutableSet.of(
            d ->  {
                d.setHandler_(EIUnicastMessage.class, new HdUnicastMessage(stack));
                d.setHandler_(EIStreamBegun.class, new HdStreamBegun(stack));
                d.setHandler_(EIChunk.class, new HdChunk(stack));
                d.setHandler_(EIStreamAborted.class, new HdStreamAborted(stack));
            }
    ));
    private final TC tc = new TC(sched, disp, queue, tokenManager, () -> {});

    // PROG RTROOT [t|z|j] ([send|stream] <did>)*
    @Override
    public void launch_(String rtRoot, String prog, String[] args)
            throws Exception
    {
        Util.initDriver("pp");
        Defects.init(prog, rtRoot);

        l.info(Arrays.toString(args));
        checkArgument(args.length % 2 == 1,
                String.format("usage: %s (t|z|j) [(send|stream) <did>]*", prog));

        for (int i = 1; i < args.length; i += 2) {
            switch (args[i].toLowerCase()) {
                case "send":
                    checkState(i + 1 < args.length);
                    producers.add(new Sender(new DID(DID.fromStringFormal(args[i + 1]))));
                    break;
                case "stream":
                    checkState(i + 1 < args.length);
                    producers.add(new Streamer(new DID(DID.fromStringFormal(args[i + 1]))));
                    break;
                default:
                    throw new IllegalArgumentException("unsupported producer: " + args[i]);
            }
        }

        iss = new IncomingStreams(stack);
        oss = new OutgoingStreams(stack);

        CoreDeviceLRU dlru = new CoreDeviceLRU();
        TransferStatisticsManager tsm = new TransferStatisticsManager();

        stack.inject_(new UnicastOutputBottomLayer.Factory(tokenManager, dlru, oss, tsm) {
                         @Override
                         public IUnicastOutputLayer create_() {
                             return new UnicastOutputBottomLayer(this) {
                                 @Override
                                 public void sendUnicastDatagram_(byte[] bs, Endpoint ep)
                                         throws ExNoResource
                                 {
                                     _f._dlru.addDevice(ep.did());

                                     EOUnicastMessage ev = new EOUnicastMessage(ep.did(), bs);
                                     ep.tp().q().enqueueBlocking(ev, TC.currentThreadPrio());
                                 }
                             };
                         }

                      },
                () -> this,
                new GlobalLimiter.Factory(sched),
                new LimitMonitor.Factory(sched, dlru),
                new IncomingStreamsThrottler(Cfg.db(), new Metrics(), iss));

        stack.init_();

        transport = newTransport(args[0]);

        // start transport
        transport.init();
        transport.start();
        linkStateService.start();

        // join the root store, so that I can actually receive presence info

        transport.q().enqueueBlocking(new EOUpdateStores(new SID[]{Cfg.rootSID()}, new SID[]{}), LO);

        // start event handling
        disp.setDefaultHandler_((incoming, prio) -> {
            if (incoming instanceof EIStoreAvailability) {
                handlePresence_((EIStoreAvailability) incoming);
            } else {
                l.warn("ignore event:{}", incoming.getClass().getSimpleName());
            }
        });
        tc.start_();

        // halt main  thread
        Object obj = new Object();
        synchronized (obj) { ThreadUtil.waitUninterruptable(obj); }
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
                queue,
                linkStateService,
                maxcastFilterReceiver,
                clientChannelFactory,
                serverChannelFactory,
                clientSslEngineFactory,
                serverSslEngineFactory,
                roundTripTimes);
    }

    @Override
    public void onUnicastDatagramReceived_(RawMessage r, PeerContext pc) {
        recvThroughputCounter.observe(r._wirelen);
        l.debug("recv incoming d:{}", pc.ep().did());
    }

    @Override
    public void onStreamBegun_(StreamID streamId, RawMessage r, PeerContext pc) {
        StreamKey key = new StreamKey(pc.ep().did(), streamId);
        try {
            recvThroughputCounter.observe(r._wirelen);
            iss.begun_(key, pc);
            Token tk = tokenManager.acquireThrows_(Cat.UNLIMITED, "rcv:" + key);
            while (true) {
                try (InputStream is = iss.recvChunk_(key, tk)) {
                    recvThroughputCounter.observe(is.available());
                }
            }
        } catch (Exception e) {
            l.warn("{} fail process stream head cause:{}", pc.ep().did(), LogUtil.suppress(e));
            iss.end_(key);
        }
    }

    @Override
    public void onStreamChunkReceived_(StreamID streamId, int seq, RawMessage r, PeerContext pc) {
        StreamKey key = new StreamKey(pc.ep().did(), streamId);
        iss.processChunk_(key, seq, r._is);
    }

    @Override
    public void onStreamAborted_(StreamID streamId, Endpoint ep, InvalidationReason reason) {
        StreamKey key = new StreamKey(ep.did(), streamId);
        iss.onAbortBySender_(key, reason);
    }

    private void handlePresence_(EIStoreAvailability presence)
    {
        for (Producer p : producers) {
            p.handlePresence_(presence._online, presence._did2sids);
        }
    }

    private abstract class Producer extends AbstractEBSelfHandling {
        protected boolean scheduled;
        protected boolean doSend;

        protected Token tk;
        protected final DID remote;

        Producer(DID did)
        {
            remote = did;
        }

        @Override
        public void handle_() {
            scheduled = false;
            if (!doSend) return;

            if (tk == null) {
                tk = tokenManager.acquire_(Cat.UNLIMITED, "snd:" + remote);
            }

            try {
                handleThrows_();
            } catch (Exception e) {
                l.warn("producer error", e);
            }

            resched_();
        }

        protected abstract void handleThrows_() throws Exception;

        protected void resched_() {
            if (scheduled) return;
            scheduled = true;
            if (!queue.enqueue_(this, TC.currentThreadPrio())) {
                sched.schedule_(this);
            }
        }

        public void handlePresence_(boolean online, ImmutableMap<DID, Collection<SID>> did2sids) {
            if (!did2sids.containsKey(remote)) return;
            if (online) {
                l.info("device reachable d:{}", remote);
                doSend = true;
                resched_();
            } else {
                l.info("device unreachable d:{}", remote);
                doSend = false;
            }
        }
    }

    private class Sender extends Pump.Producer {
        Sender(DID did)
        {
            super(did);
        }

        @Override
        public void handleThrows_() {
            try {
                stack.output().sendUnicastDatagram_(CHUNK, new Endpoint(transport, remote));
            } catch (Exception e) {
                SystemUtil.fatal(e);
            }
            sendThroughputCounter.observe(CHUNK.length);
        }
    }

    private class Streamer extends Pump.Producer
    {
        Streamer(DID did)
        {
            super(did);
        }

        @Override
        public void handleThrows_() throws Exception{
            OutgoingStream os = oss.newStream(new Endpoint(transport, remote), tk);
            try {
                while (doSend) {
                    os.sendChunk_(CHUNK);
                    sendThroughputCounter.observe(CHUNK.length);
                }
                os.end_();
            } catch (Exception e) {
                os.abort_(InvalidationReason.INTERNAL_ERROR);
                throw e;
            }
        }
    }
}

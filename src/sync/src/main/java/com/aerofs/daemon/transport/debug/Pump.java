/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.debug;

import com.aerofs.MainUtil;
import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.TimerUtil;
import com.aerofs.daemon.core.CoreEventDispatcher;
import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.net.*;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.event.net.EIDevicePresence;
import com.aerofs.daemon.event.net.rx.EIStreamBegun;
import com.aerofs.daemon.event.net.rx.EIUnicastMessage;
import com.aerofs.daemon.event.net.tx.EOUnicastMessage;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.daemon.transport.ITransport;
import com.aerofs.daemon.transport.lib.OutgoingStream;
import com.aerofs.daemon.transport.lib.RoundTripTimes;
import com.aerofs.daemon.transport.lib.StreamKey;
import com.aerofs.daemon.transport.presence.LocationManager;
import com.aerofs.daemon.transport.ssmp.SSMPConnectionService;
import com.aerofs.daemon.transport.zephyr.ZephyrParams;
import com.aerofs.defects.Defects;
import com.aerofs.ids.DID;
import com.aerofs.lib.IProgram;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.cfg.*;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.aerofs.ssmp.SSMPConnection;
import com.google.common.collect.ImmutableSet;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static com.aerofs.daemon.core.net.TransportFactory.*;
import static com.aerofs.lib.NioChannelFactories.getClientChannelFactory;
import static com.aerofs.lib.NioChannelFactories.getServerChannelFactory;
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
    private IncomingStreams iss;

    private final CoreQueue queue = new CoreQueue();


    private final CoreScheduler sched = new CoreScheduler(queue);
    private final TokenManager tokenManager = new TokenManager(Cfg.db());
    private final CoreEventDispatcher disp = new CoreEventDispatcher(ImmutableSet.of(
            d -> {
                d.setHandler_(EIUnicastMessage.class, new HdUnicastMessage(this));
                d.setHandler_(EIStreamBegun.class, new HdStreamBegun(this));
            }
    ));
    private final TC tc = new TC(queue, disp, sched, tokenManager, () -> {});

    // PROG RTROOT [t|z] ([send|stream] <did>)*
    @Override
    public void launch_(String rtRoot, String prog, String[] args)
            throws Exception
    {
        MainUtil.initDriver("pp", rtRoot);
        Defects.init(prog, rtRoot, new CfgLocalUser(), new CfgLocalDID(), new CfgVer());

        l.info(Arrays.toString(args));
        checkArgument(args.length % 2 == 1,
                String.format("usage: %s (t|z) [(send|stream) <did>]*", prog));

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

        iss = new IncomingStreams();

        Transports tps = newTransports(args[0]);

        // start transport
        tps.init_();
        tps.start_();
        linkStateService.start();

        // join the root store, so that I can actually receive presence info

        if (tps.getAll().size() != 1) {
            throw new ExUnsupportedTransport(args[0]);
        }
        transport = tps.getAll().iterator().next();
        tps.presenceSources()
                .forEach(ps -> ps.updateInterest(Cfg.rootSID(), true));

        // start event handling
        disp.setDefaultHandler_(incoming -> {
            if (incoming instanceof EIDevicePresence) {
                handlePresence_((EIDevicePresence) incoming);
            } else {
                l.warn("ignore event:{}", incoming.getClass().getSimpleName());
            }
        });
        tc.start_();

        // halt main  thread
        Object obj = new Object();
        synchronized (obj) { ThreadUtil.waitUninterruptable(obj); }
    }

    private Transports newTransports(String transportId) throws
            ExUnsupportedTransport
    {
        CfgLocalUser localid = new CfgLocalUser();
        CfgLocalDID localdid = new CfgLocalDID();
        CfgKeyManagersProvider keyProvider = new CfgKeyManagersProvider();
        CfgCACertificateProvider trustedCA = new CfgCACertificateProvider();

        CfgEnabledTransports enabled = new CfgEnabledTransports() {
            @Override public boolean isZephyrEnabled() {
                return transportId.equalsIgnoreCase("t");
            }
            @Override public boolean isTcpEnabled() {
                return transportId.equalsIgnoreCase("z");
            }
        };

        Timer timer = TimerUtil.getGlobalTimer();
        ClientSSLEngineFactory clientSslEngineFactory = new ClientSSLEngineFactory(keyProvider, trustedCA);

        SSMPConnection ssmp = new SSMPConnection(localdid.get(),
                SSMPConnection.getServerAddressFromConfiguration(), timer,
                getClientChannelFactory(), clientSslEngineFactory::newSslHandler);

        return new Transports(localid, localdid, enabled, new CfgTimeout(),
                new ZephyrParams(), timer, queue, linkStateService, clientSslEngineFactory,
                new ServerSSLEngineFactory(keyProvider, trustedCA),
                getClientChannelFactory(), getServerChannelFactory(),
                new SSMPConnectionService(queue, linkStateService, ssmp),
                new LocationManager(ssmp),
                new RoundTripTimes());
    }

    @Override
    public void onUnicastDatagramReceived_(RawMessage r, PeerContext pc) {
        try {
            recvThroughputCounter.observe(r._is.available());
        } catch (IOException e) {
            l.warn("{}", e.getMessage());
        }
        l.debug("recv incoming d:{}", pc.ep().did());
    }

    @Override
    public void onStreamBegun_(StreamID streamId, RawMessage r, PeerContext pc) {
        StreamKey key = new StreamKey(pc.ep().did(), streamId);
        try {
            iss.begun_(key, pc);
            tokenManager.inPseudoPause_(Cat.UNLIMITED, "rcv:" + key, () -> {
                try (InputStream is = r._is) {
                    int n;
                    byte[] buf = new byte[4096];
                    while ((n = is.read(buf)) != -1) {
                        recvThroughputCounter.observe(n);
                    }
                }
                l.info("{} done process stream", pc.ep().did());
                return null;
            });
        } catch (Exception e) {
            l.warn("{} fail process stream head cause:{}", pc.ep().did(), BaseLogUtil.suppress(e));
            iss.end_(key);
        }
    }

    private void handlePresence_(EIDevicePresence presence)
    {
        for (Producer p : producers) {
            p.handlePresence_(presence._did, presence._online);
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
            sched.schedule_(this);
        }

        public void handlePresence_(DID did, boolean online) {
            if (!did.equals(remote)) return;
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
            transport.q().enqueueBlocking(new EOUnicastMessage(remote, CHUNK), TC.currentThreadPrio());
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
            OutgoingStream os = transport.newOutgoingStream(remote);
            try {
                while (doSend) {
                    os.write(CHUNK);
                    sendThroughputCounter.observe(CHUNK.length);
                }
                l.info("done send stream {}", remote);
            } catch (Exception e) {
                os.abort(InvalidationReason.INTERNAL_ERROR);
                throw e;
            } finally {
                os.close();
            }
        }
    }
}

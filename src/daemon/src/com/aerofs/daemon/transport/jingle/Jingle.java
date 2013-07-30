/*
* Copyright (c) Air Computing Inc., 2013.
*/

package com.aerofs.daemon.transport.jingle;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.event.lib.EventDispatcher;
import com.aerofs.daemon.event.net.EOTpStartPulse;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.BlockingPrioQueue;
import com.aerofs.daemon.lib.DaemonParam.XMPP;
import com.aerofs.daemon.transport.TransportThreadGroup;
import com.aerofs.daemon.transport.lib.HdPulse;
import com.aerofs.daemon.transport.lib.IMaxcast;
import com.aerofs.daemon.transport.lib.ITransportImpl;
import com.aerofs.daemon.transport.lib.IUnicast;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.daemon.transport.lib.PulseManager;
import com.aerofs.daemon.transport.lib.TransportProtocolHandler;
import com.aerofs.daemon.transport.lib.StreamManager;
import com.aerofs.daemon.transport.lib.TPUtil;
import com.aerofs.daemon.transport.lib.TransportStats;
import com.aerofs.daemon.transport.netty.ClientHandler;
import com.aerofs.daemon.transport.netty.IUnicastCallbacks;
import com.aerofs.daemon.transport.netty.Unicast;
import com.aerofs.daemon.transport.xmpp.Multicast;
import com.aerofs.daemon.transport.xmpp.PresenceStore;
import com.aerofs.daemon.transport.xmpp.StartPulse;
import com.aerofs.daemon.transport.xmpp.XMPPConnectionService;
import com.aerofs.daemon.transport.xmpp.XMPPPresenceManager;
import com.aerofs.daemon.transport.xmpp.XMPPUtilities;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.ex.ExDeviceOffline;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.sched.Scheduler;
import com.aerofs.proto.Files.PBDumpStat;
import com.aerofs.proto.Files.PBDumpStat.Builder;
import com.aerofs.proto.Files.PBDumpStat.PBTransport;
import com.aerofs.rocklog.RockLog;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.slf4j.Logger;

import java.io.PrintStream;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.util.Set;

import static com.aerofs.daemon.transport.lib.TPUtil.registerMulticastHandler;


public class Jingle implements ITransportImpl, IUnicastCallbacks
{
    private static final Logger l = Loggers.getLogger(Jingle.class);
    private static final String SIGNAL_THREAD_THREAD_ID = "st";

    private final BlockingPrioQueue<IEvent> _q;
    private final IBlockingPrioritizedEventSink<IEvent> _sink;
    private final EventDispatcher _disp = new EventDispatcher();
    private final Scheduler _sched;
    private final TransportStats _transportStats;
    private final PresenceStore _presenceStore = new PresenceStore();
    private final PulseManager _pulseManager = new PulseManager();
    private final StreamManager _sm = new StreamManager();
    private final DID _localDID;
    private final String _id;
    private final int _rank;
    private final SignalThread _signalThread;
    private final Multicast _multicast;
    private final XMPPConnectionService _xmppServer;
    private final Unicast _ucast;

    public Jingle(UserID localUser, DID localDID, byte[] scrypted, String absRtRoot, String id, int rank,
            IBlockingPrioritizedEventSink<IEvent> sink, MaxcastFilterReceiver mcfr, RockLog rockLog,
            SSLEngineFactory clientSslEngineFactory,
            SSLEngineFactory serverSslEngineFactory
            )
    {
        _localDID = localDID;
        _id = id;
        _rank = rank;
        _sink = sink;
        _q = new BlockingPrioQueue<IEvent>(XMPP.QUEUE_LENGTH);
        _sched = new Scheduler(_q, id);
        _transportStats = new TransportStats();

        _xmppServer = new XMPPConnectionService(localDID, id, scrypted, rockLog);

        // Signal thread
        OSUtil.get().loadLibrary("aerofsj");
        _signalThread = new SignalThread(localDID, _xmppServer, absRtRoot);
        _signalThread.setDaemon(true);
        _signalThread.setName(SIGNAL_THREAD_THREAD_ID);

        // Unicast
        _ucast = new Unicast(this, _transportStats);
        TransportProtocolHandler protocolHandler = new TransportProtocolHandler(this, sink, _sm, _pulseManager);
        JingleBootstrapFactory bsFact = new JingleBootstrapFactory(localUser, localDID, clientSslEngineFactory, serverSslEngineFactory, _transportStats);
        ServerBootstrap serverBootstrap = bsFact.newServerBootstrap(_signalThread, _ucast,
                protocolHandler);
        ClientBootstrap clientBootstrap = bsFact.newClientBootstrap(_signalThread);
        _ucast.setBootstraps(serverBootstrap, clientBootstrap);

        // Presence Manager
        XMPPPresenceManager presenceManager = new XMPPPresenceManager(this, localDID, sink,
                _presenceStore, _pulseManager, _ucast);

        // Multicast
        _multicast = new Multicast(localDID, id, mcfr, _xmppServer, this, sink);
        registerMulticastHandler(this);

        // Warning: it is very important that XMPPPresenceManager listens to the XMPPServer _before_
        // Multicast. The reason is that Multicast will join the chat rooms and this will trigger
        // sending the presence information. So if we add Multicast as a listener first, the presence
        // information will already be sent by the time the presence manager registers to get them.
        _xmppServer.addListener(presenceManager);
        _xmppServer.addListener(_multicast);
    }

    @Override
    public void init_()
            throws Exception
    {
        TPUtil.registerCommonHandlers(this);
    }

    @Override
    public void start_()
    {
        // Event loop
        new Thread(TransportThreadGroup.get(), new Runnable() {
            @Override
            public void run()
            {
                OutArg<Prio> outPrio = new OutArg<Prio>();
                while (true) {
                    IEvent ev = _q.dequeue(outPrio);
                    _disp.dispatch_(ev, outPrio.get());
                }
            }
        }, id()).start();

        _signalThread.start();

        _ucast.start(new DIDAddress(_localDID));
    }

    @Override
    public HdPulse<EOTpStartPulse> sph()
    {
        return new HdPulse<EOTpStartPulse>(new StartPulse<Jingle>(this, _presenceStore));
    }

    @Override
    public EventDispatcher disp()
    {
        return _disp;
    }

    @Override
    public Scheduler sched()
    {
        return _sched;
    }

    @Override
    public IUnicast ucast()
    {
        return _ucast;
    }

    @Override
    public IMaxcast mcast()
    {
        return _multicast;
    }

    @Override
    public PulseManager pm()
    {
        return _pulseManager;
    }

    @Override
    public StreamManager sm()
    {
        return _sm;
    }

    @Override
    public void disconnect_(DID did)
    {
        _ucast.disconnect(did, new Exception("forced disconnect"));
    }

    @Override
    public void updateStores_(SID[] sidAdded, SID[] sidRemoved)
    {
        _multicast.updateStores_(sidAdded, sidRemoved);
    }

    @Override
    public void linkStateChanged_(Set<NetworkInterface> removed, Set<NetworkInterface> added,
            Set<NetworkInterface> prev, Set<NetworkInterface> current)
            throws ExNoResource
    {
        final boolean upBefore = !prev.isEmpty();
        final boolean upNow = !current.isEmpty();

        // Going down
        if (upBefore && !upNow) {
            _ucast.pauseAccept();
            _xmppServer.linkStateChanged(false);
        }

        // Going up
        if (!upBefore && upNow) {
            _ucast.resumeAccept();
            // If the signal thread is not ready, do not start the xmpp server. The signal thread
            // will start the xmpp server when it's ready.
            if (_signalThread.isReady()) _xmppServer.linkStateChanged(true);
        }
    }

    @Override
    public boolean supportsMulticast()
    {
        return true;
    }

    @Override
    public IBlockingPrioritizedEventSink<IEvent> q()
    {
        return _q;
    }

    @Override
    public void dumpStat(PBDumpStat dstemplate, final Builder dsbuilder)
            throws Exception
    {
        // TODO (GS): Copied from TCP

        PBTransport tp = dstemplate.getTransport(0);
        assert tp != null : ("called dumpstat with null tp");

        PBTransport.Builder tpbuilder = PBTransport.newBuilder();
        if (tp.hasName()) tpbuilder.setName(id());

        dsbuilder.addTransport(tpbuilder);

        try {
            _ucast.dumpStat(dstemplate, dsbuilder);
        } catch (Exception e) {
            // FIXME: put in a message saying there was an error
        }
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
            throws Exception
    {
        String indent2 = indent + indentUnit;

        ps.println(indent + "q");
        _q.dumpStatMisc(indent2, indentUnit, ps);

        ps.println(indent + "xmpp");
        _xmppServer.dumpStatMisc(indent2, indentUnit, ps);

        ps.println(indent + "ucast");
        _signalThread.dumpStatMisc(indent2, indentUnit, ps);
    }

    @Override
    public String id()
    {
        return _id;
    }

    @Override
    public int rank()
    {
        return _rank;
    }

    @Override
    public long bytesIn()
    {
        return _transportStats.getBytesReceived();
    }

    @Override
    public long bytesOut()
    {
        return _transportStats.getBytesSent();
    }

    @Override
    public SocketAddress resolve(DID did)
            throws ExDeviceOffline
    {
        return new DIDAddress(did);
    }

    @Override
    public void closePeerStreams(DID did, boolean outbound, boolean inbound)
    {
        TPUtil.sessionEnded(new Endpoint(this, did), _sink, _sm, outbound, inbound);
    }

    @Override
    public void onClientCreated(ClientHandler client)
    {
        // nothing to do
    }
}

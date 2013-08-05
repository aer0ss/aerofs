/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.zephyr;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.JabberID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.base.ssl.SSLEngineFactory;
import com.aerofs.daemon.event.lib.EventDispatcher;
import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.daemon.event.net.EIPresence;
import com.aerofs.daemon.event.net.EOTpStartPulse;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.BlockingPrioQueue;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.mobile.MobileServerZephyrConnector;
import com.aerofs.daemon.transport.TransportThreadGroup;
import com.aerofs.daemon.transport.exception.ExSendFailed;
import com.aerofs.daemon.transport.lib.HdPulse;
import com.aerofs.daemon.transport.lib.IMaxcast;
import com.aerofs.daemon.transport.lib.ITransportImpl;
import com.aerofs.daemon.transport.lib.IUnicast;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.daemon.transport.lib.PulseManager;
import com.aerofs.daemon.transport.lib.PulseManager.GenericPulseDeletionWatcher;
import com.aerofs.daemon.transport.lib.StreamManager;
import com.aerofs.daemon.transport.lib.TPUtil;
import com.aerofs.daemon.transport.lib.TransportDiagnosisState;
import com.aerofs.daemon.transport.lib.TransportStats;
import com.aerofs.daemon.transport.xmpp.ISignallingService;
import com.aerofs.daemon.transport.xmpp.ISignallingServiceListener;
import com.aerofs.daemon.transport.xmpp.Multicast;
import com.aerofs.daemon.transport.xmpp.PresenceStore;
import com.aerofs.daemon.transport.xmpp.StartPulse;
import com.aerofs.daemon.transport.xmpp.XMPPConnectionService;
import com.aerofs.daemon.transport.xmpp.XMPPConnectionService.IXMPPConnectionServiceListener;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.sched.Scheduler;
import com.aerofs.proto.Files.PBDumpStat;
import com.aerofs.proto.Transport.PBCheckPulse;
import com.aerofs.proto.Transport.PBStream;
import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.proto.Transport.PBTransportDiagnosis;
import com.aerofs.rocklog.RockLog;
import com.google.common.collect.ImmutableMap;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.SASLAuthentication;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.MessageTypeFilter;
import org.jivesoftware.smack.filter.OrFilter;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.NetworkInterface;
import java.net.Proxy;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.Set;

import static com.aerofs.base.id.JabberID.did2FormAJid;
import static com.aerofs.daemon.lib.DaemonParam.Zephyr.QUEUE_LENGTH;
import static com.aerofs.daemon.transport.lib.PulseManager.newCheckPulseReply;
import static com.aerofs.daemon.transport.lib.TPUtil.makeDiagnosis;
import static com.aerofs.daemon.transport.lib.TPUtil.processUnicastControlDiagnosis;
import static com.aerofs.daemon.transport.lib.TPUtil.processUnicastHeader;
import static com.aerofs.daemon.transport.lib.TPUtil.registerCommonHandlers;
import static com.aerofs.daemon.transport.lib.TPUtil.registerMulticastHandler;
import static com.aerofs.daemon.transport.lib.TPUtil.sessionEnded;
import static com.aerofs.daemon.transport.xmpp.XMPPUtilities.decodeBody;
import static com.aerofs.daemon.transport.xmpp.XMPPUtilities.encodeBody;
import static com.aerofs.proto.Transport.PBTPHeader.Type.STREAM;
import static com.aerofs.proto.Transport.PBTPHeader.Type.TRANSPORT_CHECK_PULSE_CALL;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.jivesoftware.smack.packet.Message.Type.chat;
import static org.jivesoftware.smack.packet.Message.Type.error;
import static org.jivesoftware.smack.packet.Message.Type.groupchat;
import static org.jivesoftware.smack.packet.Message.Type.headline;

public final class Zephyr implements ITransportImpl, IUnicast, IConnectionServiceListener, IXMPPConnectionServiceListener, ISignallingService
{
    //
    // members
    //

    protected static final Logger l = Loggers.getLogger(Zephyr.class);

    private final String id;
    private final int rank; // FIXME (AG): why does the transport need to know its own preference

    private final DID localdid;

    private final IBlockingPrioritizedEventSink<IEvent> outgoingEventSink;
    private final BlockingPrioQueue<IEvent> eventQueue = new BlockingPrioQueue<IEvent>(QUEUE_LENGTH);
    private final EventDispatcher eventDispatcher = new EventDispatcher();
    private final Scheduler scheduler;
    private Thread dispatcherThread;

    private final TransportDiagnosisState transportDiagnosisState = new TransportDiagnosisState();
    private final PulseManager pulseManager = new PulseManager();
    private final StreamManager streamManager = new StreamManager();
    private final MaxcastFilterReceiver maxcastFilterReceiver;

    private final PresenceStore presenceStore = new PresenceStore();
    private final Multicast multicast;
    private final XMPPConnectionService xmppConnectionService;

    private final ZephyrConnectionService zephyrConnectionService;

    private final MobileServerZephyrConnector mobileZephyrConnector; // FIXME (AG): this shouldn't be here
    private final TransportStats transportStats = new TransportStats();

    private boolean multicastEnabled = false;

    //
    // methods
    //

    public Zephyr(
            UserID localid, DID localdid,
            byte[] scrypted,
            String id, int rank,
            IBlockingPrioritizedEventSink<IEvent> outgoingEventSink,
            MaxcastFilterReceiver maxcastFilterReceiver,
            SSLEngineFactory clientSSLEngineFactory,
            SSLEngineFactory serverSSLEngineFactory,
            ClientSocketChannelFactory clientSocketChannelFactory,
            MobileServerZephyrConnector mobileZephyr,
            RockLog rocklog,
            SocketAddress zephyrAddress,
            Proxy proxy)
    {
        checkState(DaemonParam.XMPP.CONNECT_TIMEOUT > DaemonParam.Zephyr.HANDSHAKE_TIMEOUT); // should be much larger!

        // this is a workaround for NullPointerException during authentication
        // see http://www.igniterealtime.org/community/thread/35976
        SASLAuthentication.supportSASLMechanism("PLAIN", 0);

        this.localdid = localdid;
        this.id = id;
        this.rank = rank;

        this.mobileZephyrConnector = mobileZephyr; // I don't want this here!!!!
        this.outgoingEventSink = outgoingEventSink;
        this.scheduler = new Scheduler(eventQueue, id());
        this.xmppConnectionService = new XMPPConnectionService(localdid, id(), scrypted, this, rocklog);
        this.multicast = new Multicast(this, maxcastFilterReceiver, xmppConnectionService, outgoingEventSink, localdid, id());
        this.maxcastFilterReceiver = maxcastFilterReceiver;
        this.pulseManager.addPulseDeletionWatcher(new GenericPulseDeletionWatcher(this, this.outgoingEventSink));

        this.zephyrConnectionService = new ZephyrConnectionService(
                localid, localdid,
                clientSSLEngineFactory,
                serverSSLEngineFactory,
                this,
                this,
                transportStats,
                rocklog,
                clientSocketChannelFactory,
                zephyrAddress,
                proxy);
    }

    public void enableMulticast()
    {
        l.debug("{}: enabling multicast", id());

        multicastEnabled = true;
        registerMulticastHandler(this);
    }

    @Override
    public boolean supportsMulticast()
    {
        return multicastEnabled;
    }

    @Override
    public void init_()
            throws Exception
    {
        registerCommonHandlers(this);
        zephyrConnectionService.init();
    }

    @Override
    public void start_()
    {
        checkState(dispatcherThread == null, "dispatcher thread already started");

        dispatcherThread = new Thread(TransportThreadGroup.get(), new Runnable()
        {
            @Override
            public void run()
            {
                OutArg<Prio> outPrio = new OutArg<Prio>();
                // noinspection InfiniteLoopStatement
                while (true) {
                    IEvent ev = eventQueue.dequeue(outPrio);
                    eventDispatcher.dispatch_(ev, outPrio.get());
                }
            }
        }, id());

        dispatcherThread.start();
        zephyrConnectionService.start();
    }

    @Override
    public boolean ready()
    {
        return xmppConnectionService.ready() && zephyrConnectionService.ready();
    }

    @Override
    public String id()
    {
        return id;
    }

    @Override
    public int rank()
    {
        return rank;
    }

    //--------------------------------------------------------------------------
    //
    //
    // FIXME (AG): completely refactor how signalling messages are sent via XMPP (see IQ)
    // ISignallingService methods (how subsystems can send/receive messages via
    // a control channel)
    //
    //
    //--------------------------------------------------------------------------

    @Override
    public void registerSignallingClient(ISignallingServiceListener client)
    {
        confirmZephyrConnectionService(client);
    }

    @Override
    public void sendSignallingMessage(DID did, byte[] msg, ISignallingServiceListener client)
    {
        confirmZephyrConnectionService(client);

        OutArg<Integer> len = new OutArg<Integer>(0);
        String enc = encodeBody(len, msg);

        final Message xmsg = new Message(did2FormAJid(did, id()), Message.Type.normal);
        xmsg.setBody(enc);

        // for now we actually don't have to enqueue the sending task as a new
        // event since sendPacket() is synchronized, but it's easy to do so if we
        // have to for whatever reason (i.e. I've tried both approaches and this
        // signature supports both). This allows us to implement this method
        // however we wish, with whatever synchronization style we want

        try {
            xmppServerConnection().conn().sendPacket(xmsg);
        } catch (XMPPException e) {
            notifyZephyrConnectionServiceOfSignallingFailure(client, did, msg, e);
        } catch (IllegalStateException e) {
            // NOTE: this can happen because smack considers it illegal to attempt to send
            // a packet if the channel is not connected. Since we may be notified of a
            // disconnection after actually enqueuing the packet to be sent, it's entirely
            // possible for this to occur
            notifyZephyrConnectionServiceOfSignallingFailure(client, did, msg, e);
        }
    }

    private void notifyZephyrConnectionServiceOfSignallingFailure(ISignallingServiceListener client, DID did, byte[] msg, Exception e)
    {
        confirmZephyrConnectionService(client);

        zephyrConnectionService.sendSignallingMessageFailed(did, msg, e);
    }

    private void confirmZephyrConnectionService(ISignallingServiceListener client)
    {
        checkArgument(client == zephyrConnectionService, "exp:" + zephyrConnectionService.getClass().getSimpleName() + " act:" + client.getClass().getSimpleName());
    }

    @Override
    public void xmppServerConnected(XMPPConnection conn)
            throws XMPPException
    {
        l.info("register packet listeners");

        conn.addPacketListener(new PacketListener()
        {
            @Override
            public void processPacket(Packet packet)
            {
                if (packet instanceof Message) {
                    Message m = (Message)packet;
                    if (m.getSubject() != null) return;

                    Message.Type t = m.getType();

                    checkArgument(t != groupchat && t != headline && t != chat, "pl: groupchat, headline and chat messages are not expected here");
                    checkArgument(t != error, "pl: errors and headlines are unhandled here");

                    try {
                        processMessage(m);
                    } catch (ExFormatError e) {
                        logXmppProcessingError("pl: badly formatted message", e, packet);
                    } catch (Exception e) {
                        logXmppProcessingError("pl: cannot process valid message", e, packet);

                        // we fatal for a number of reasons here:
                        // - an exception from disconnect within a subsystem
                        //   is unrecoverable. It means that we're trying
                        //   to recover from an error condition, but our
                        //   recovery process is failing. in this case we
                        //   really shouldn't go on
                        // - we cannot reschedule this event because ordering
                        //   is extremely important in processing XMPP
                        //   messages. Receiving online, offline messages
                        //   is very different than offline, online

                        SystemUtil.fatal(e);
                    }
                } else if (packet instanceof Presence) {
                    try {
                        processPresence((Presence) packet);
                    } catch (Exception e) {
                        logXmppProcessingError("pl: cannot process_ mc presence from " + packet.getFrom(), e, packet);
                    }
                }
            }
        }, new OrFilter(new MessageTypeFilter(Message.Type.normal), new PacketTypeFilter(Presence.class)));

        try {
            multicast.xmppServerConnected(null); // FIXME: should not pass in null - change Multicast to use passed-in connection
            zephyrConnectionService.signallingServiceConnected();
        } catch (XMPPException e) {
            l.warn("invalid XMPP message on XMPP connection");
        }

        if (mobileZephyrConnector != null) {
            mobileZephyrConnector.setConnection(conn);
        }
    }

    private void processMessage(Message m)
            throws ExFormatError
    {
        try {
            DID did = JabberID.jid2did(m.getFrom());
            OutArg<Integer> wirelen = new OutArg<Integer>(0);
            byte[] decoded = decodeBody(did, wirelen, m.getBody(), maxcastFilterReceiver);
            if (decoded == null) return;
            zephyrConnectionService.processIncomingSignallingMessage(did, decoded);
        } catch (IOException e) {
            l.warn(Util.e(e));
            return;
        }
    }

    private void logXmppProcessingError(String errmsg, Exception e, Packet packet)
    {
        l.warn(errmsg + " from:" + packet.getFrom() + " err: " + Util.e(e));
    }

    @Override
    public long bytesIn()
    {
        return transportStats.getBytesReceived();
    }

    @Override
    public long bytesOut()
    {
        return transportStats.getBytesSent();
    }

    //--------------------------------------------------------------------------
    //
    //
    // ITransport methods (required by core)
    //
    //
    //--------------------------------------------------------------------------

    @Override
    public IBlockingPrioritizedEventSink<IEvent> q()
    {
        return eventQueue;
    }

    //--------------------------------------------------------------------------
    //
    //
    // ITransportImpl methods (accessors required by handlers) FIXME: remove accessors
    //
    //
    //--------------------------------------------------------------------------

    @Override
    public EventDispatcher disp()
    {
        return eventDispatcher;
    }

    @Override
    public Scheduler sched()
    {
        return scheduler;
    }

    @Override
    public HdPulse<EOTpStartPulse> sph()
    {
        return new HdPulse<EOTpStartPulse>(new StartPulse<Zephyr>(this, presenceStore));
    }

    @Override
    public IUnicast ucast()
    {
        return this;
    }

    @Override
    public IMaxcast mcast()
    {
        return multicast;
    }

    @Override
    public PulseManager pm()
    {
        return pulseManager;
    }

    @Override
    public StreamManager sm()
    {
        return streamManager;
    }

    @Override
    public TransportDiagnosisState tds()
    {
        return transportDiagnosisState;
    }

    public IBlockingPrioritizedEventSink<IEvent> sink()
    {
        return outgoingEventSink;
    }

    public XMPPConnectionService xmppServerConnection()
    {
        return xmppConnectionService;
    }

    @Override
    public void updateStores_(SID[] sidsAdded, SID[] sidsRemoved)
    {
        multicast.updateStores_(sidsAdded, sidsRemoved);
    }

    //--------------------------------------------------------------------------
    //
    //
    // IUnicast methods
    //
    //
    //--------------------------------------------------------------------------

    @Override
    public Object send(DID did, IResultWaiter waiter, Prio priority, byte[][] bss, Object cookie)
            throws Exception
    {
        return zephyrConnectionService.send(did, waiter, priority, bss, cookie);
    }

    //--------------------------------------------------------------------------
    //
    //
    // IConnectionServiceListener event methods - subsystems should use these methods
    // to have this class process incoming events
    //
    //
    //--------------------------------------------------------------------------

    @Override
    public void onDeviceConnected(DID did)
    {
        l.info("d:{} connected - closing streams", did);
        sessionEnded(new Endpoint(Zephyr.this, did), outgoingEventSink, streamManager, true, true);
    }

    @Override
    public void onDeviceDisconnected(DID did)
    {
        l.info("d:{} disconnected - closing streams", did);
        sessionEnded(new Endpoint(Zephyr.this, did), outgoingEventSink, streamManager, true, true);
    }

    //
    // process incoming transport messages
    //

    private void processStreamControl(DID did, PBTPHeader hdr)
            throws ExProtocolError
    {
        PBTPHeader.Type type = hdr.getType();
        checkArgument(type == STREAM, "d:" + did + " recv invalid hdr type:" + type.name());
        checkArgument(hdr.getStream().getType() != PBStream.Type.PAYLOAD, "invalid stream hdr type:" + hdr.getStream().getType());

        Endpoint ep = new Endpoint(this, did);
        try {
            PBTPHeader ret = TPUtil.processUnicastControl(ep, hdr, outgoingEventSink, streamManager);
            sendControl(did, ret, Prio.LO);
        } catch (ExNoResource e) {
            l.warn("fail notify core of incoming control d:{} t:{}", did, hdr.getType().name());
            disconnect(did, e);
        }
    }

    private void processPulseControl(DID did, PBCheckPulse cp, boolean cpcall)
    {
        int pulseid = cp.getPulseId();
        if (cpcall) {
            l.info("rcv pulse req msgpulseid:" + pulseid + " d:" + did);
            sendControl(did, newCheckPulseReply(pulseid), Prio.HI);
        } else {
            l.info("rcv pulse rep msgpulseid:" + pulseid + " d:" + did);
            pulseManager.processIncomingPulseId(did, pulseid);
        }
    }

    private void processDiagnosis(DID did, PBTransportDiagnosis dg)
            throws ExProtocolError
    {
        PBTransportDiagnosis dgret = processUnicastControlDiagnosis(did, dg, zephyrConnectionService, transportDiagnosisState);
        if (dgret != null) {
            PBTPHeader ret = makeDiagnosis(dgret);
            sendControl(did, ret, Prio.LO);
        }
    }

    private void sendControl(DID did, @Nullable PBTPHeader hdr, Prio pri)
    {
        if (hdr == null) {
            l.debug("null return");
            return;
        }

        try {
            zephyrConnectionService.send(did, null, pri, TPUtil.newControl(hdr), null);
        } catch (Exception e) {
            l.warn("could not respond to d:" + did + " pkt:" + hdr.getType().name() + " err:" + e);
            disconnect(did, e);
        }
    }

    private void processUnicastControl(DID did, PBTPHeader hdr)
    {
        PBTPHeader.Type type = hdr.getType();
        try {
            switch (type) {
            case TRANSPORT_CHECK_PULSE_CALL:
                //noinspection fallthrough
            case TRANSPORT_CHECK_PULSE_REPLY:
                PBCheckPulse cp = hdr.getCheckPulse();
                checkNotNull(cp, "invalid pulse msg from d:" + did);
                processPulseControl(did, cp, (type == TRANSPORT_CHECK_PULSE_CALL));
                break;
            case DIAGNOSIS:
                PBTransportDiagnosis dg = hdr.getDiagnosis();
                checkNotNull(dg, "invalid diagnosis from d:" + did);
                processDiagnosis(did, dg);
                break;
            default:
                processStreamControl(did, hdr);
                break;
            }
        } catch (ExProtocolError e) {
            l.warn("fail process pkt d:{} t:{}", did, hdr.getType().name());
            disconnect(did, e);
        }
    }

    private void processUnicastPayload(DID did, UserID userID, PBTPHeader hdr, InputStream bodyis, int wirelen)
    {
        try {
            Endpoint ep = new Endpoint(Zephyr.this, did);
            PBTPHeader ret = TPUtil.processUnicastPayload(ep, userID, hdr, bodyis, wirelen, outgoingEventSink, streamManager);
            if (ret != null) sendControl(did, ret, Prio.LO);
        } catch (Exception e) {
            String errorMessage = "could not respond to d:" + did + " for pkt:" + hdr.getType().name() + " err:" + e;
            l.warn(errorMessage);
            disconnect(did, new ExSendFailed(errorMessage, e));
        }
    }

    private @Nullable PBTPHeader extractHeader(DID did, InputStream packet)
    {
        PBTPHeader hdr = null;
        try {
            hdr = processUnicastHeader(packet);
        } catch (IOException e) {
            l.warn("fail extract hdr d:{} err:{}", did, e.getMessage());
            disconnect(did, e);
        }
        return hdr;
    }

    @Override
    public void onIncomingMessage(DID did, UserID userID, InputStream packet, int wirelen)
    {
        PBTPHeader transhdr = extractHeader(did, packet);
        if (transhdr == null) return;

        if (!TPUtil.isPayload(transhdr)) {
            processUnicastControl(did, transhdr);
        } else {
            processUnicastPayload(did, userID, transhdr, packet, wirelen);
        }
    }

    //--------------------------------------------------------------------------
    //
    //
    // IXMPPConnectionServiceListener methods (how XMPP gets notified and
    // processes methods coming in on the control channel)
    //
    //
    //--------------------------------------------------------------------------

    @Override
    public void xmppServerDisconnected()
    {
        l.warn("xsc noticed disconnect");

        outgoingEventSink.enqueueBlocking( new EIPresence(Zephyr.this, false, ImmutableMap.<DID, Collection<SID>>of()), Prio.LO);
        multicast.xmppServerDisconnected();
        zephyrConnectionService.signallingServiceDisconnected();
    }

    //--------------------------------------------------------------------------
    //
    //
    // Internal methods - should only be called within XMPP event-dispatch thread
    //
    //
    //--------------------------------------------------------------------------

    @Override
    public final void disconnect_(DID did)
    {
        disconnect(did, new Exception("forced disconnect"));
    }

    private void disconnect(DID did, Exception cause)
    {
        zephyrConnectionService.disconnect(did, cause);
    }

    @Override
    public final void linkStateChanged_(Set<NetworkInterface> removed, Set<NetworkInterface> added, Set<NetworkInterface> prev, Set<NetworkInterface> current)
    {
        xmppConnectionService.linkStateChanged(current);
        zephyrConnectionService.linkStateChanged(removed);
    }

    //
    // presence methods
    //

    private void processPresence(Presence p)
            throws ExFormatError
    {
        // NOTE: if the device goes offline then _zm will catch this since
        // the TCP connection via Zephyr will break

        String[] tokens = JabberID.tokenize(p.getFrom());
        if (!JabberID.isMUCAddress(tokens)) return;
        if (JabberID.isMobileUser(tokens[1])) return;
        if (tokens.length == 3 && (tokens[2].compareToIgnoreCase(id()) != 0))
            return; // ignore presence from other xmpp-based transports

        SID sid = JabberID.muc2sid(tokens[0]);
        DID did = JabberID.user2did(tokens[1]);
        if (did.equals(localdid)) return;

        if (p.isAvailable()) {
            l.info("recv online presence d:" + did);
            presenceStore.add(did, sid);
            pulseManager.stopPulse(did, false);
        } else {
            zephyrConnectionService.disconnect(did, new Exception("remote offline"));
            boolean waslast = presenceStore.del(did, sid);
            if (waslast) {
                l.info("recv offline presence d:" + did);
                pulseManager.stopPulse(did, false);
            }
        }

        outgoingEventSink.enqueueBlocking(new EIPresence(this, p.isAvailable(), did, sid), Prio.LO);
    }

    @Override
    public String toString()
    {
        return id();
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
    {
        String indent2 = indent + indentUnit;

        ps.println(indent + "q");
        eventQueue.dumpStatMisc(indent2, indentUnit, ps);
        ps.println(indent + "mcast");
        xmppConnectionService.dumpStatMisc(indent2, indentUnit, ps);
        ps.println(indent + "ucast");
        zephyrConnectionService.dumpStatMisc(indent2, indentUnit, ps);
    }

    @Override
    public void dumpStat(PBDumpStat template, PBDumpStat.Builder bd)
    {
        zephyrConnectionService.dumpStat(template, bd);
    }
}

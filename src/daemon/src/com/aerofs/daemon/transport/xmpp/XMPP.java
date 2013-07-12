package com.aerofs.daemon.transport.xmpp;

import com.aerofs.base.Base64;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.JabberID;
import com.aerofs.base.id.SID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.event.lib.EventDispatcher;
import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.daemon.event.net.EIPresence;
import com.aerofs.daemon.event.net.EOTpStartPulse;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.BlockingPrioQueue;
import com.aerofs.daemon.transport.TransportThreadGroup;
import com.aerofs.daemon.transport.exception.ExSendFailed;
import com.aerofs.daemon.transport.lib.HdPulse;
import com.aerofs.daemon.transport.lib.IConnectionServiceListener;
import com.aerofs.daemon.transport.lib.IMaxcast;
import com.aerofs.daemon.transport.lib.ITransportImpl;
import com.aerofs.daemon.transport.lib.IUnicast;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.daemon.transport.lib.PulseManager;
import com.aerofs.daemon.transport.lib.PulseManager.GenericPulseDeletionWatcher;
import com.aerofs.daemon.transport.lib.StreamManager;
import com.aerofs.daemon.transport.lib.TPUtil;
import com.aerofs.daemon.transport.lib.TransportDiagnosisState;
import com.aerofs.daemon.transport.xmpp.XMPPServerConnection.IXMPPServerConnectionWatcher;
import com.aerofs.daemon.transport.xmpp.routing.ConnectionServiceWrapper;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.IBlockingPrioritizedEventSink;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.sched.Scheduler;
import com.aerofs.proto.Files.PBDumpStat;
import com.aerofs.proto.Transport.PBCheckPulse;
import com.aerofs.proto.Transport.PBStream.Type;
import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.proto.Transport.PBTransportDiagnosis;
import com.aerofs.rocklog.RockLog;
import com.google.common.collect.ImmutableMap;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.SASLAuthentication;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.NetworkInterface;
import java.util.Collection;
import java.util.Set;

import static com.aerofs.daemon.lib.DaemonParam.MAX_TRANSPORT_MESSAGE_SIZE;
import static com.aerofs.daemon.lib.DaemonParam.XMPP.QUEUE_LENGTH;
import static com.aerofs.daemon.transport.lib.PulseManager.newCheckPulseReply;
import static com.aerofs.daemon.transport.lib.TPUtil.makeDiagnosis;
import static com.aerofs.daemon.transport.lib.TPUtil.newControl;
import static com.aerofs.daemon.transport.lib.TPUtil.processUnicastControlDiagnosis;
import static com.aerofs.proto.Transport.PBTPHeader.Type.DATAGRAM;
import static com.aerofs.proto.Transport.PBTPHeader.Type.DIAGNOSIS;
import static com.aerofs.proto.Transport.PBTPHeader.Type.STREAM;
import static com.aerofs.proto.Transport.PBTPHeader.Type.TRANSPORT_CHECK_PULSE_CALL;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Acts as a controller (or wrapper) over a number of {@link IConnectionService} implementations
 * that use an XMPP server as an out-of-band signalling channel. At a high level
 * this is primarily implemented as a single-threaded event processor. It also
 * switches between controlled <code>IConnectionService</code> instances at runtime based on
 * availability.
 * <br/>
 * <br/>
 * <strong>IMPORTANT:</strong> XMPP is implemented as a single-threaded event
 * processor that controls multiple <code>IConnectionService</code> instances. While these
 * can be implemented in any way (multi-threaded, single-threaded event processor,
 * etc.) the current implementations are all single-threaded event processors.
 * To prevent deadlock, the following convention <strong>MUST</strong> be
 * followed: <code>XMPP</code> <strong>MUST</strong> <code>enqueueThrows</code>
 * into controlled <code>IConnectionService</code> instances while <code>IConnectionService</code> instances
 * <strong>MUST</strong> <code>enqueueBlocking</code> into <code>XMPP</code>.
 * <br/>
 * <br/>
 * Practially speaking, this means:
 * <ol>
 *     <li>All implemented <code>IConnectionServiceListener</code> methods <strong>MUST</strong>
 *         use <code>enqueueBlocking</code></li>
 *     <li>All implemented <code>IConnectionService</code> methods for a single-threaded
 *         event processing <code>IConnectionService</code> <strong>MUST</strong> use
 *         <code>enqueueThrows</code></li>
 * </ol>
 */
public abstract class XMPP implements ITransportImpl, IConnectionServiceListener, IUnicast, IXMPPServerConnectionWatcher
{
    protected XMPP(DID localdid, byte[] scrypted, String id, int rank, IBlockingPrioritizedEventSink<IEvent> sink, MaxcastFilterReceiver maxcastFilterReceiver, RockLog rocklog)
    {
        // this is a workaround for NullPointerException during authentication
        // see http://www.igniterealtime.org/community/thread/35976
        SASLAuthentication.supportSASLMechanism("PLAIN", 0);

        _localdid = localdid;
        _id = id;
        _rank = rank;
        _sink = sink;
        _sched = new Scheduler(_q, id());
        _xsc = new XMPPServerConnection(localdid, id(), scrypted, this, rocklog);
        _mc = new Multicast(this, localdid, id());
        _mcfr = maxcastFilterReceiver;
        _pm.addPulseDeletionWatcher(new GenericPulseDeletionWatcher(this, _sink));
    }

    protected final void setConnectionService_(ISignalledConnectionService connectionService)
    {
        _spf = new ConnectionServiceWrapper(_sched, connectionService);
    }

    @Override
    public void init_() throws Exception
    {
        TPUtil.registerCommonHandlers(this);
        _spf.init_();
    }

    @Override
    public void start_()
    {
        assert _dispthr == null : ("cannot start dispatcher twice");

        _dispthr = new Thread(TransportThreadGroup.get(), new Runnable()
        {
            @Override
            public void run()
            {
                OutArg<Prio> outPrio = new OutArg<Prio>();
                // noinspection InfiniteLoopStatement
                while (true) {
                    IEvent ev = _q.dequeue(outPrio);
                    _disp.dispatch_(ev, outPrio.get());
                }
            }
        }, id());

        _dispthr.start();
        _spf.start_();
    }

    @Override
    public final boolean ready()
    {
        return _xsc.ready() && _spf.ready();
    }

    @Override
    public final String id()
    {
        return _id;
    }

    @Override
    public final int rank()
    {
        return _rank;
    }

    //--------------------------------------------------------------------------
    //
    //
    // ITransport methods (required by core)
    //
    //
    //--------------------------------------------------------------------------

    @Override
    public final IBlockingPrioritizedEventSink<IEvent> q()
    {
        return _q;
    }

    //--------------------------------------------------------------------------
    //
    //
    // ITransportImpl methods (accessors required by handlers) FIXME: remove accessors
    //
    //
    //--------------------------------------------------------------------------

    @Override
    public final EventDispatcher disp()
    {
        return _disp;
    }

    @Override
    public final Scheduler sched()
    {
        return _sched;
    }

    @Override
    public final HdPulse<EOTpStartPulse> sph()
    {
        return new HdPulse<EOTpStartPulse>(new StartPulse(this));
    }

    @Override
    public final IUnicast ucast()
    {
        return this;
    }

    @Override
    public final IMaxcast mcast()
    {
        return _mc;
    }

    @Override
    public final PulseManager pm()
    {
        return _pm;
    }

    @Override
    public final StreamManager sm() {
        return _sm;
    }

    @Override
    public final TransportDiagnosisState tds()
    {
        return _tds;
    }

    public final IBlockingPrioritizedEventSink<IEvent> sink()
    {
        return _sink;
    }

    public final XMPPServerConnection xmppServerConnection()
    {
        return _xsc;
    }

    public final XMPPPresenceManager xpm()
    {
        return _xpm;
    }

    @Override
    public final void updateStores_(SID[] sidsAdded, SID[] sidsRemoved)
    {
        assertDispThread();

        _mc.updateStores_(sidsAdded, sidsRemoved);
    }

    protected String getXmppPassword()
    {
        return _xsc.getXmppPassword();
    }

    //--------------------------------------------------------------------------
    //
    //
    // IUnicast methods
    //
    //
    //--------------------------------------------------------------------------

    // (AG): ideally I should delegate to <code>ConnectionServiceWrapper</code>
    // unfortunately it would need too much access to XMPP's state

    @Override
    public final Object send(final DID did, final IResultWaiter wtr, final Prio pri,
            final byte[][] bss, final Object cke)
        throws Exception
    {
        assert _dispthr != null : ("null dispthr");

        final OutArg<Object> retcke = new OutArg<Object>(null);

        if (Thread.currentThread() == _dispthr) {
            retcke.set(_spf.send_(did, wtr, pri, bss, cke));
        } else {
            // FIXME (AG): I need to generalize this pattern (I suspect it'll be nasty)

            //
            // it's correct to synchronize on a local object here because I'm simply using it
            // for wait/notify not for mutual exclusion
            //

            final Object lock = new Object();

            // noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (lock) {
                enqueueIntoXmpp(new AbstractEBSelfHandling()
                {
                    @Override
                    public void handle_()
                    {
                        synchronized (lock) {
                            try {
                                retcke.set(_spf.send_(did, wtr, pri, bss, cke));
                            } catch (Exception e) {
                                if (wtr != null) wtr.error(e);
                            } finally {
                                lock.notifyAll();
                            }
                        }
                    }
                }, pri);

                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    assert false : ("interrupted during send d:" + did);
                }
            }
        }

        assert retcke.get() != null : ("null return cookie");

        return retcke.get();
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
    public final void onDeviceConnected(final DID d, final IConnectionService cs)
    {
        assertNonDispThread();

        enqueueIntoXmpp(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                l.info("d:{} connected - closing streams", d);
                TPUtil.sessionEnded(new Endpoint(XMPP.this, d), _sink, _sm, true, true);
                _spf.deviceConnected_(d, cs);
            }
        }, Prio.LO);
    }

    @Override
    public final void onDeviceDisconnected(final DID d, final IConnectionService cs)
    {
        //
        // when I first implemented this method I assumed that it would always be called in
        // the context of a pipe's event-queue thread. this is not the case. _especially_ with
        // jingle there are two ways in which this method can be called: 1) when there's a
        // null main and a connect task fails immediately, and 2) when an actual disconnect occurs
        // in 1) this is happening in the xmpp thread; in 2) this happens in jingle's signal thread
        //

        AbstractEBSelfHandling disconnectTask = new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                l.info("d:{} disconnected - closing streams", d);
                TPUtil.sessionEnded(new Endpoint(XMPP.this, d), _sink, _sm, true, true);
                _spf.deviceDisconnected_(d, cs);
            }
        };

        if (Thread.currentThread() == _dispthr) {
            disconnectTask.handle_();
        } else {
            enqueueIntoXmpp(disconnectTask, Prio.LO);
        }
    }

    //
    // process incoming transport messages
    //

    /**
     * Process an incoming unicast control message from a peer inside the
     * <code>XMPP</code> event-dispatch thread
     * <br/>
     * <br/>
     * <strong>IMPORTANT:</strong> asserts that this method is <em>only</em>
     * called from within the <code>XMPP</code> event-dispatch thread
     * <br/>
     * <br/>
     * <strong>IMPORTANT:</strong> if the control packet <em>cannot</em> be
     * processed by the <code>Core</code> because of resource constraints this
     * method will assert
     *
     * @param did {@link DID} that sent the control packet
     * @param hdr {@link PBTPHeader} where the type is <em>not</em> <code>PAYLOAD</code>
     * or <code>DIAGNOSIS</code>. <strong>IMPORTANT:</strong> asserts that
     * <code>hdr</code> does not have an unhandled type.
     * @throws ExProtocolError if the control packet has an unrecognized (and therefore
     * unprocessable) type
     */
    private void processOtherUnicastControl(DID did, PBTPHeader hdr)
        throws ExProtocolError
    {
        PBTPHeader.Type type = hdr.getType();
        assert type != DATAGRAM && type != DIAGNOSIS : ("invalid hdr type:" + type.name());
        if (type == STREAM) {
            assert hdr.getStream().getType() != Type.PAYLOAD : ("invalid stream hdr type:" + hdr.getStream().getType());
        }

        Endpoint ep = new Endpoint(this, did);
        try {
            PBTPHeader ret = TPUtil.processUnicastControl(ep, hdr, _sink, _sm);
            sendControl(did, ret, Prio.LO);
        } catch (ExNoResource e) {
            l.warn("fail notify core of incoming control d:{} t:{}", did, hdr.getType().name());
            disconnect(did, e);
        }
    }

    /**
     * Process an incoming pulse control message from a peer inside the
     * <code>XMPP</code> event-dispatch thread. This message
     * <em>should</em>be of type <code>TRANSPORT_CHECK_PULSE_CALL</code> or
     * <code>TRANSPORT_CHECK_PULSE_REPLY</code>.
     * <br/>
     * <br/>
     * <strong>IMPORTANT:</strong> asserts that this method is <em>only</em>
     * called from within the <code>XMPP</code> event-dispatch thread
     *
     * @param did {@link DID} of the peer that sent the pulse control message
     * @param cp {@link PBCheckPulse} pulse control message received from the peer
     * @param cpcall <code>true</code>if it is of type
     * <code>TRANSPORT_CHECK_PULSE_CALL</code>, <code>false</code> if it is of
     * type <code>TRANSPORT_CHECK_PULSE_REPLY</code>
     */
    private void processPulseControl(DID did, PBCheckPulse cp, boolean cpcall)
    {
        int pulseid = cp.getPulseId();
        if (cpcall) {
            l.info("rcv pulse req msgpulseid:" + pulseid + " d:" + did);
            sendControl(did, newCheckPulseReply(pulseid), Prio.HI);
        } else {
            l.info("rcv pulse rep msgpulseid:" + pulseid + " d:" + did);
            _pm.processIncomingPulseId(did, pulseid);
        }
    }

    /**
     * Process an incoming {@link PBTransportDiagnosis} diagnostic control message from a
     * peer inside the <code>XMPP</code> event-dispatch thread
     * <br/>
     * <br/>
     * <strong>IMPORTANT:</strong> asserts that this method is <em>only</em>
     * called from within the <code>XMPP</code> event-dispatch thread
     *
     * @param did {@link DID} of the peer that sent the diagnostic message
     * @param dg {@link PBTransportDiagnosis} diagnostic message sent by the peer
     * @throws ExProtocolError if the diagnostic message has a type that is unrecognized
     * (and therefore unprocessable) by this method
     */
    private void processDiagnosis(DID did, PBTransportDiagnosis dg)
        throws ExProtocolError
    {
        PBTransportDiagnosis dgret = processUnicastControlDiagnosis(did, dg, _spf, _tds);
        if (dgret != null) {
            PBTPHeader ret = makeDiagnosis(dgret);
            sendControl(did, ret, Prio.LO);
        }
    }

    /**
     * Provides a uniform way to send control responses to a peer inside the
     * <code>XMPP</code> event-dispatch thread. It allows (and will not send)
     * a <code>null</code> packet and logs an exception thrown during the
     * <code>send</code> call
     * <br/>
     * <br/>
     * Messages that should be sent out using this method include (among others):
     * <ul>
     *     <li>Payload responses</li>
     *     <li>Pulse calls/responses</li>
     *     <li>Flood, Ping, and other diagnostic packets</li>
     *     <li>Generic control responses</li>
     * </ul>
     * <strong>IMPORTANT:</strong> asserts that this method is <em>only</em>
     * called from within the <code>XMPP</code> event-dispatch thread
     *
     * @param did {@link DID} of the peer to which response will be sent
     * @param hdr {@link PBTPHeader} to be send to the peer. <code>hdr</code> can
     * be <code>null</code>, in which case <code>sendControl</code> acts as a
     * no-op
     * @param pri {@link Prio} priority with which the message is scheduled for
     * sending
     */
    private void sendControl(final DID did, @Nullable final PBTPHeader hdr, final Prio pri)
    {
        if (hdr == null) {
            l.debug("null return");
            return;
        }

        AbstractEBSelfHandling controlEvent = new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                try {
                    _spf.send_(did, null, pri, newControl(hdr), null);
                } catch (Exception e) {
                    l.warn("could not respond to d:" + did + " pkt:" + hdr.getType().name() + " err:" + e);
                    disconnect(did, e);
                }
            }
        };

        if (Thread.currentThread() == _dispthr) {
            controlEvent.handle_();
        } else {
            enqueueIntoXmpp(controlEvent, Prio.LO);
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
                    assert cp != null : ("invalid pulse msg from d:" + did);
                    processPulseControl(did, cp, (type == TRANSPORT_CHECK_PULSE_CALL));
                    break;
                case DIAGNOSIS:
                    PBTransportDiagnosis dg = hdr.getDiagnosis();
                    assert dg != null : ("invalid diagnosis from d:" + did);
                    processDiagnosis(did, dg);
                    break;
                default:
                    processOtherUnicastControl(did, hdr);
                    break;
            }
        } catch (ExProtocolError e) {
            l.warn("fail process pkt d:{} t:{}", did, hdr.getType().name());
            disconnect(did, e);
        }
    }

    private void processUnicastPayload(final DID did, UserID userID, PBTPHeader hdr, InputStream bodyis, int wirelen)
    {
        try {
            Endpoint ep = new Endpoint(XMPP.this, did);
            final PBTPHeader ret = TPUtil.processUnicastPayload(ep, userID, hdr, bodyis, wirelen, _sink, _sm);
            if (ret != null) sendControl(did, ret, Prio.LO);
        } catch (Exception e) {
            String errorMessage = "could not respond to d:" + did + " for pkt:" + hdr.getType().name() + " err:" + e;
            l.warn(errorMessage);
            disconnect(did, new ExSendFailed(errorMessage, e));
        }
    }

    private @Nullable PBTPHeader extractHeader(final DID did, InputStream packet)
    {
        PBTPHeader hdr = null;
        try {
            hdr = TPUtil.processUnicastHeader(packet);
        } catch (final IOException e) {
            l.warn("fail extract hdr d:{} err:{}", did, e.getMessage());
            disconnect(did, e);
        }
        return hdr;
    }

    @Override
    public final void onIncomingMessage(final DID did, final UserID userID, final InputStream packet, final int wirelen)
    {
        assertNonDispThread();

        final PBTPHeader transhdr = extractHeader(did, packet);
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
    // IXMPPServerConnectionWatcher methods (how XMPP gets notified and
    // processes methods coming in on the control channel)
    //
    //
    //--------------------------------------------------------------------------

    @Override
    public void xmppServerDisconnected()
    {
        l.warn("x: xsc noticed disconnect");

        //
        // IMPORTANT: there are two entry points (threading-wise) into this method:
        // 1) the Smack thread notices a networking error and triggers its listener
        // 2) a link-state-change from n interfaces -> 0 interfaces shuts down the Smack thread
        //
        // In 1) this method is called on Smack's thread, while in 2) its called on the
        // event-dispatching thread
        //

        AbstractEBSelfHandling serverDisconnectedEvent = new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                try {
                    _sink.enqueueBlocking(new EIPresence(XMPP.this, false, ImmutableMap.<DID, Collection<SID>>of()), Prio.LO);
                    _mc.xmppServerDisconnected();
                    _spf.xmppServerDisconnected_();
                } catch (ExNoResource e) {
                    l.error("cannot handle XMPP disconnection ev");

                    // must fatal here because if a subsystem cannot handle
                    // processing this event we cannot reschedule it (because event
                    // ordering is extremely important)

                    SystemUtil.fatal(e);
                }
            }
        };

        Thread thr = Thread.currentThread();
        if (thr == _dispthr) {
            serverDisconnectedEvent.handle_(); // happened in the disp thr, so run directly
        } else {
            enqueueIntoXmpp(serverDisconnectedEvent, Prio.HI);
        }
    }

    @Override
    public void xmppServerConnected(final XMPPConnection conn) throws XMPPException
    {
        assertNonDispThread();

        final PacketTypeFilter presfilter = new PacketTypeFilter(Presence.class);

        conn.addPacketListener(new PacketListener()
        {
            @Override
            public void processPacket(final Packet packet)
            {
                enqueueIntoXmpp(new AbstractEBSelfHandling()
                {
                    @Override
                    public void handle_()
                    {
                        if (packet instanceof Presence) {
                            try {
                                processPresence_((Presence) packet);
                            } catch (Exception e) {
                                l.warn("pl: cannot process_ mc presence from "
                                    + packet.getFrom() + ": "
                                    + Util.e(e, ExFormatError.class));
                            }
                        }
                    }
                }, Prio.LO);
            }
        }, presfilter);

        enqueueIntoXmpp(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                try {
                    _spf.xmppServerConnected_();
                    _mc.xmppServerConnected(null); // FIXME: should not pass in null - change Multicast to use passed-in connection
                } catch (XMPPException e) {
                    l.warn("invalid XMPP message on XMPP connection");
                } catch (ExNoResource e) { // FIXME: should I catch all exceptions? - no...let me approach this on a case-by-case basis for now
                    l.error("cannot handle XMPP connection ev");

                    // must fatal here because if a subsystem cannot handle
                    // processing this event we cannot reschedule it (because event
                    // ordering is extremely important)

                    SystemUtil.fatal(e);
                }
            }
        }, Prio.HI);
    }

    /**
     * Enqueue a method for processing by the <code>XMPP</code> {@link EventDispatcher}.
     * The thread calling this method will <em>block</em> until the event can
     * be enqueued. The event is run from within the event-dispatch thread and
     * can safely use the non-thread-safe methods
     *
     * @param ev {@link IEvent} to be run in the event-dispatch thread
     * @param pri {@link Prio} priority of the event
     */
    protected void enqueueIntoXmpp(IEvent ev, Prio pri)
    {
        assertNonDispThread();

        try {
            _q.enqueueThrows(ev, pri);
        } catch (ExNoResource e) {
            l.warn("fail enq ev " + ev.getClass().getName() + " - resched for immediate ex");

            // TODO (EK) remove sampling thread once OOM fixed
            if (!_isSamplerThreadActive) {
                _isSamplerThreadActive = true;
                Runnable runnable = new Runnable()
                {
                    @Override
                    public void run()
                    {
                        long period = 500; // sample every 500 ms
                        int MAX = 50, counter = 0;
                        while(counter < MAX) {
                            Util.logAllThreadStackTraces();
                            ThreadUtil.sleepUninterruptable(period);
                            counter++;
                        }
                    }
                };
                ThreadUtil.startDaemonThread("sampler", runnable);
            }

            _sched.schedule(ev, 0);
        }
    }

    private void disconnect(final DID did, final Exception cause)
    {
        AbstractEBSelfHandling disconnectEvent = new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                try {
                    _spf.disconnect_(did, cause);
                } catch (ExNoResource e) {
                    SystemUtil.fatal("fail enqueue disconnect event into xmpp q");
                }
            }
        };

        if (Thread.currentThread() == _dispthr) {
            disconnectEvent.handle_();
        } else {
            enqueueIntoXmpp(disconnectEvent, Prio.HI);
        }
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
        throws ExNoResource
    {
        assertDispThread();

        disconnect(did, new Exception("forced disconnect"));
    }

    @Override
    public final void linkStateChanged_(
            Set<NetworkInterface> removed,
            Set<NetworkInterface> added,
            Set<NetworkInterface> prev,
            Set<NetworkInterface> current)
        throws ExNoResource
    {
        assertDispThread();

        _xsc.linkStateChanged(current);
        _spf.linkStateChanged_(removed, current);
    }

    //
    // presence methods
    //

    /**
     * Process an XMPP presence packet from within the <code>XMPP</code>
     * event-dispatch thread
     * <br/>
     * <br/>
     * <strong>IMPORTANT:</strong> asserts that this method is <em>only</em>
     * called from within the <code>XMPP</code> event-dispatch thread
     *
     * @param p presence packet to process
     * @throws ExFormatError if the <code>JID</code> cannot be converted to a
     * <code>DID</code>
     * @throws ExNoResource if a request to disconnect from a peer
     * cannot be processed by {@link com.aerofs.daemon.transport.xmpp.routing.ConnectionServiceWrapper} due to resource constraints
     * within an {@link ISignalledConnectionService}
     */
    private void processPresence_(Presence p)
        throws ExFormatError, ExNoResource
    {
        assertDispThread();

        // NOTE: if the device goes offline then _zm will catch this since
        // the TCP connection via Zephyr will break

        String[] tokens = JabberID.tokenize(p.getFrom());
        if (!JabberID.isMUCAddress(tokens)) return;
        if (JabberID.isMobileUser(tokens[1])) return;
        if (tokens.length == 3 && (tokens[2].compareToIgnoreCase(id()) != 0)) return; // ignore presence from other xmpp-based transports

        SID sid = JabberID.muc2sid(tokens[0]);
        DID did = JabberID.user2did(tokens[1]);
        if (did.equals(_localdid)) return;

        if (p.isAvailable()) {
            l.info("recv online presence d:" + did);
            _xpm.add(did, sid);
            _pm.stopPulse(did, false);
        } else {
            _spf.disconnect_(did, new Exception("remote offline"));
            boolean waslast = _xpm.del(did, sid);
            if (waslast) {
                l.info("recv offline presence d:" + did);
                _pm.stopPulse(did, false);
            }
        }

        _sink.enqueueBlocking(new EIPresence(this, p.isAvailable(), did, sid), Prio.LO);
    }

    //
    // message (de)serialization methods
    //

    /**
     * Wrapper for encodeBody for subsystems that do not use Maxcast filtering
     * e.g., ZephyrClientManager
     *
     * @param outLen will be populated with the number of bytes that will be sent over the wire
     * @param bss bytes to encode
     * @return encoded string ready for transport over the XMPP channel
     */
    public static String encodeBody(OutArg<Integer> outLen, byte[] ... bss)
    {
        return encodeBody(outLen, MAXCAST_UNFILTERED, bss);
    }

    public static String encodeBody(OutArg<Integer> outLen, int mcastid, byte[] ... bss)
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            DataOutputStream os = new DataOutputStream(new Base64.OutputStream(bos));

            os.writeInt(LibParam.CORE_MAGIC);

            // TODO consider adding mcastid to chksum?
            // if so, don't forget to check in decodeBody
            os.writeInt(mcastid);

            int len = 0;
            for (byte[] bs : bss) len += bs.length;
            os.writeInt(len);

            byte chksum = 0;
            for (byte[] bs : bss) {
                for (byte b : bs) chksum ^= b;
                os.write(bs);
            }

            os.write(chksum);

            os.close();

            // add the size of headers and footers
            outLen.set(len + HEADER_LEN);

        } catch (IOException e) {
            SystemUtil.fatal(e);
        }

        return bos.toString();
    }

    /**
     * Decode the body of an incoming XMPP message
     *
     * FIXME: Change this to be static by passing _mcfr in
     *
     * @param did {@link DID} of the remote peer from whom the message was received
     * @param wirelen will be populated with the number of bytes the message took
     * up on the wire
     * @param body the encoded body of the XMPP message
     * @return null if magic number doesn't match or it's a duplicate, or a decoded
     * message body otherwise
     * @throws IOException if the message cannot be decoded
     */
    @Nullable byte[] decodeBody(DID did, OutArg<Integer> wirelen, String body)
            throws IOException
    {
        ByteArrayInputStream bos = new ByteArrayInputStream(body.getBytes());
        DataInputStream is = null;
        try {
            is = new DataInputStream(new Base64.InputStream(bos));

            int magic = is.readInt();
            if (magic != LibParam.CORE_MAGIC) {
                l.warn("magic mismatch " +
                        "d:" + did + " exp:" + LibParam.CORE_MAGIC + " act:" + magic + " bdy:" + body);

                return null;
            }

            // Parse the maxcast id.
            // Do not attempt to filter away if it is an UNFILTERED packet
            int mcastid = is.readInt();
            if (MAXCAST_UNFILTERED != mcastid && _mcfr.isRedundant(did, mcastid)) {
                return null;
            }

            int len = is.readInt();
            if (len <= 0 || len > MAX_TRANSPORT_MESSAGE_SIZE) {
                throw new IOException("insane msg len " + len);
            }

            byte[] bs = new byte[len];
            try {
                is.readFully(bs);

                int read = is.read();
                if (read == -1) throw new IOException("chksum not present");

                byte chksum = (byte) read;
                for (byte b : bs) chksum ^= b;
                if (chksum != 0) throw new IOException("chksum mismatch");

            } catch (EOFException e) {
                throw new IOException("msg len " + len + " > actual");
            }

            if (bos.available() != 0) {
                throw new IOException("msg len " + len + " < avail by " + bos.available());
            }

            wirelen.set(len + HEADER_LEN);
            return bs;
        } finally {
            if (is != null) is.close();
        }
    }

    //
    // network utility methods
    //

    /**
     * NOTE: does the same comparison as EOLinkStateChanged used to. Here to
     * maintain compatibility for components that don't need the full link set
     *
     * @param current set of remaining network interfaces
     * @return true if there are no active (up) network interfaces; false if at least
     * one interface is active
     */
    public static boolean allLinksDown(Set<NetworkInterface> current)
    {
        return current.isEmpty();
    }

    //
    // thread utility methods
    //

    /**
     * Asserts that the current method is being called from within the
     * <code>XMPP</code> {@link EventDispatcher} thread
     */
    protected final void assertDispThread()
    {
        checkNotNull(_dispthr, "null disp thr");
        checkState(Thread.currentThread() == _dispthr,
                "method called from non-disp thd:" + Thread.currentThread().getName());
    }

    /**
     * Asserts that the current method is <em>not</em> being called from within
     * the <code>XMPP</code> {@link EventDispatcher} thread*
     */
    protected final void assertNonDispThread()
    {
        checkNotNull(_dispthr, "null disp thr");
        checkState(Thread.currentThread() != _dispthr, "method called from disp thd");
    }

    //
    // printing methods
    //

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
        _q.dumpStatMisc(indent2, indentUnit, ps);
        ps.println(indent + "mcast");
        _xsc.dumpStatMisc(indent2, indentUnit, ps);
        ps.println(indent + "ucast");
        _spf.dumpStatMisc(indent2, indentUnit, ps);
    }

    @Override
    public void dumpStat(PBDumpStat template, PBDumpStat.Builder bd)
    {
        _spf.dumpStat(template, bd);
    }

    //
    // members
    //

    private final DID _localdid;
    private final String _id;
    private final int _rank;

    private boolean _isSamplerThreadActive = false;

    private final IBlockingPrioritizedEventSink<IEvent> _sink;
    private final BlockingPrioQueue<IEvent> _q = new BlockingPrioQueue<IEvent>(QUEUE_LENGTH);
    private final EventDispatcher _disp = new EventDispatcher();
    private final Scheduler _sched;
    private Thread _dispthr;

    private final TransportDiagnosisState _tds = new TransportDiagnosisState();
    private final PulseManager _pm = new PulseManager();
    private final StreamManager _sm = new StreamManager();
    private final MaxcastFilterReceiver _mcfr;

    private final XMPPPresenceManager _xpm = new XMPPPresenceManager();
    private final Multicast _mc;
    private final XMPPServerConnection _xsc;
    private ConnectionServiceWrapper _spf;

    protected static final Logger l = Loggers.getLogger(XMPP.class);

    //
    // constants
    //

    private final static int HEADER_LEN = 2 * C.INTEGER_SIZE + 1;
    private final static int MAXCAST_UNFILTERED = -1;
}

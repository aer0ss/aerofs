package com.aerofs.daemon.transport.xmpp;

import com.aerofs.daemon.event.IEvent;
import com.aerofs.daemon.event.lib.AbstractEBSelfHandling;
import com.aerofs.daemon.event.lib.EventDispatcher;
import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.daemon.event.net.EIPresence;
import com.aerofs.daemon.event.net.EOTpStartPulse;
import com.aerofs.daemon.event.net.EOTransportReconfigRemoteDevice;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.BlockingPrioQueue;
import com.aerofs.daemon.lib.IBlockingPrioritizedEventSink;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.Scheduler;
import com.aerofs.daemon.mobile.MobileServerZephyrConnector;
import com.aerofs.daemon.mobile.MobileService;
import com.aerofs.daemon.transport.lib.HdPulse;
import com.aerofs.daemon.transport.lib.IMaxcast;
import com.aerofs.daemon.transport.lib.INetworkStats.BasicStatsCounter;
import com.aerofs.daemon.transport.lib.IPipeController;
import com.aerofs.daemon.transport.lib.ITransportImpl;
import com.aerofs.daemon.transport.lib.IUnicast;
import com.aerofs.daemon.transport.lib.MaxcastFilterReceiver;
import com.aerofs.daemon.transport.lib.PulseManager;
import com.aerofs.daemon.transport.lib.PulseManager.GenericPulseDeletionWatcher;
import com.aerofs.daemon.transport.lib.StreamManager;
import com.aerofs.daemon.transport.lib.TPUtil;
import com.aerofs.daemon.transport.lib.TransportDiagnosisState;
import com.aerofs.daemon.transport.xmpp.XMPPServerConnection.IXMPPServerConnectionWatcher;
import com.aerofs.daemon.transport.xmpp.jingle.Jingle;
import com.aerofs.daemon.transport.xmpp.routing.SignalledPipeFanout;
import com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientManager;
import com.aerofs.lib.Base64;
import com.aerofs.lib.C;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExFormatError;
import com.aerofs.lib.ex.ExNoResource;
import com.aerofs.lib.ex.ExProtocolError;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.proto.Files.PBDumpStat;
import com.aerofs.proto.Transport.PBCheckPulse;
import com.aerofs.proto.Transport.PBStream.Type;
import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.proto.Transport.PBTransportDiagnosis;
import org.apache.log4j.Logger;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.SASLAuthentication;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.MessageTypeFilter;
import org.jivesoftware.smack.filter.OrFilter;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.PrintStream;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.aerofs.daemon.lib.DaemonParam.MAX_TRANSPORT_MESSAGE_SIZE;
import static com.aerofs.daemon.lib.DaemonParam.XMPP.PACKETROUTE.JINGLE;
import static com.aerofs.daemon.lib.DaemonParam.XMPP.PACKETROUTE.ZEPHYR;
import static com.aerofs.daemon.lib.DaemonParam.XMPP.QUEUE_LENGTH;
import static com.aerofs.daemon.transport.lib.PulseManager.newCheckPulseReply;
import static com.aerofs.daemon.transport.lib.TPUtil.makeDiagnosis;
import static com.aerofs.daemon.transport.lib.TPUtil.newControl;
import static com.aerofs.daemon.transport.lib.TPUtil.processUnicastControlDiagnosis;
import static com.aerofs.lib.C.NOSTUN;
import static com.aerofs.lib.C.NOZEPHYR;
import static com.aerofs.proto.Transport.PBTPHeader.Type.DATAGRAM;
import static com.aerofs.proto.Transport.PBTPHeader.Type.DIAGNOSIS;
import static com.aerofs.proto.Transport.PBTPHeader.Type.STREAM;
import static com.aerofs.proto.Transport.PBTPHeader.Type.TRANSPORT_CHECK_PULSE_CALL;
import static org.jivesoftware.smack.packet.Message.Type.chat;
import static org.jivesoftware.smack.packet.Message.Type.error;
import static org.jivesoftware.smack.packet.Message.Type.groupchat;
import static org.jivesoftware.smack.packet.Message.Type.headline;

/**
 * Acts as a controller (or wrapper) over a number of {@link IPipe} implementations
 * that use an XMPP server as an out-of-band signalling channel. At a high level
 * this is primarily implemented as a single-threaded event processor. It also
 * switches between controlled <code>IPipe</code> instances at runtime based on
 * availability.
 * <br/>
 * <br/>
 * <strong>IMPORTANT:</strong> XMPP is implemented as a single-threaded event
 * processor that controls multiple <code>IPipe</code> instances. While these
 * can be implemented in any way (multi-threaded, single-threaded event processor,
 * etc.) the current implementations are all single-threaded event processors.
 * To prevent deadlock, the following convention <strong>MUST</strong> be
 * followed: <code>XMPP</code> <strong>MUST</strong> <code>enqueueThrows</code>
 * into controlled <code>IPipe</code> instances while <code>IPipe</code> instances
 * <strong>MUST</strong> <code>enqueueBlocking</code> into <code>XMPP</code>.
 * <br/>
 * <br/>
 * Practially speaking, this means:
 * <ol>
 *     <li>All implemented <code>IPipeController</code> methods <strong>MUST</strong>
 *         use <code>enqueueBlocking</code></li>
 *     <li>All implemented <code>IPipe</code> methods for a single-threaded
 *         event processing <code>IPipe</code> <strong>MUST</strong> use
 *         <code>enqueueThrows</code></li>
 * </ol>
 */
public class XMPP implements ITransportImpl, IPipeController, IUnicast, ISignallingChannel,
        IXMPPServerConnectionWatcher
{
    // TODO use DI
    private final InjectableFile.Factory _factFile = new InjectableFile.Factory();

    // TODO (EK) remove once OOM fixed
    private boolean _isSamplerThreadActive = false;

    /**
     * @param sink
     * @param mcfr
     * @param mobileServiceFactory
     */
    public XMPP(IBlockingPrioritizedEventSink<IEvent> sink, MaxcastFilterReceiver mcfr,
            MobileService.Factory mobileServiceFactory)
    {
        // this is a workaround for NullPointerException during authentication
        // see http://www.igniterealtime.org/community/thread/35976
        SASLAuthentication.supportSASLMechanism("PLAIN", 0);

        _sink = sink;
        _mcfr = mcfr;
        _pm.addPulseDeletionWatcher(new GenericPulseDeletionWatcher(this, _sink));
        _cw = new XMPPServerConnection(ID.resource(false), this);

        //
        // setup the packet router
        //

        boolean okj = !_factFile.create(Cfg.absRTRoot(), NOSTUN).exists();
        boolean okz = !_factFile.create(Cfg.absRTRoot(), NOZEPHYR).exists();

        assert okj || okz : ("at least one PR component must be active");

        BasicStatsCounter sc = new BasicStatsCounter();
        Set<ISignalledPipe> pipes = new HashSet<ISignalledPipe>();

        if (okj) {
            pipes.add(new Jingle(JINGLE.id(), JINGLE.pref(), this, sc));
        }
        if (okz) {
            // FIXME: hmm separate concerns?
            pipes.add(new ZephyrClientManager(ZEPHYR.id(), ZEPHYR.pref(), this, sc, this));
        }

        _spf = new SignalledPipeFanout(_sched, pipes);
        if (mobileServiceFactory != null && MobileService.Factory.isEnabled()) {
            _mobileConnector = new MobileServerZephyrConnector(mobileServiceFactory);
        } else {
            _mobileConnector = null;
        }
    }

    @Override
    public void init_() throws Exception
    {
        TPUtil.registerCommonHandlers(this);
        _disp.setHandler_(EOTransportReconfigRemoteDevice.class,
                new HdTransportReconfigRemoteDevice());

        _spf.init_();
    }

    @Override
    public void start_()
    {
        assert _dispthr == null : ("cannot start dispatcher twice");

        _dispthr = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                OutArg<Prio> outPrio = new OutArg<Prio>();
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
    public boolean ready()
    {
        return _cw.ready() && _spf.ready();
    }

    @Override
    public String id()
    {
        return C.TRANSPORT_ID_XMPP;
    }

    @Override
    public int pref()
    {
        return 1;
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
        return _q;
    }

    @Override
    public Set<DID> getMulticastUnreachableOnlineDevices()
    {
        return Collections.emptySet();
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
        return _disp;
    }

    @Override
    public Scheduler sched()
    {
        return _sched;
    }

    @Override
    public HdPulse<EOTpStartPulse> sph()
    {
        return new HdPulse<EOTpStartPulse>(new StartPulse(this));
    }

    @Override
    public IUnicast ucast()
    {
        return this;
    }

    @Override
    public IMaxcast mcast()
    {
        return _mc;
    }

    @Override
    public PulseManager pm()
    {
        return _pm;
    }

    @Override
    public StreamManager sm() {
        return _sm;
    }

    @Override
    public TransportDiagnosisState tds()
    {
        return _tds;
    }

    /**
     *
     * @return
     */
    public IBlockingPrioritizedEventSink<IEvent> sink()
    {
        return _sink;
    }

    /**
     *
     * @return
     */
    public XMPPServerConnection cw()
    {
        return _cw;
    }

    /**
     *
     * @return
     */
    public XMPPPresenceManager xpm()
    {
        return _xpm;
    }

    @Override
    public void updateStores_(SID[] sidsAdded, SID[] sidsRemoved)
    {
        assertDispThread();

        _mc.updateStores_(sidsAdded, sidsRemoved);
    }

    //--------------------------------------------------------------------------
    //
    //
    // IUnicast methods
    //
    //
    //--------------------------------------------------------------------------

    // FIXME: ideally I should delegate to <code>SignalledPipeFanout</code> but it would need too much access to XMPP's state

    @Override
    public Object send_(final DID did, final IResultWaiter wtr, final Prio pri, final byte[][] bss, final Object cke)
        throws Exception
    {
        assert _dispthr != null : ("null dispthr");

        final OutArg<Object> retcke = new OutArg<Object>(null);

        if (Thread.currentThread() == _dispthr) {
            retcke.set(_spf.send_(did, wtr, pri, bss, cke));
        } else {
            // FIXME: I need to generalize this pattern (I suspect it'll be nasty)

            final Object cvobj = new Object();

            synchronized (cvobj) {
                enqueueIntoXmpp(new AbstractEBSelfHandling()
                {
                    @Override
                    public void handle_()
                    {
                        synchronized (cvobj) {
                            try {
                                retcke.set(_spf.send_(did, wtr, pri, bss, cke));
                            } catch (Exception e) {
                                if (wtr != null) wtr.error(e);
                            } finally {
                                cvobj.notifyAll();
                            }
                        }
                    }
                }, pri);

                try {
                    cvobj.wait();
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
    // IPipeController event methods - use these methods to enqueue an event into
    // XMPP for processing within the event dispatch thread. should be used by
    // subsystems that XMPP controls
    //
    //
    //--------------------------------------------------------------------------

    @Override
    public void peerConnected(final DID d, IPipe.ConnectionType type, final IPipe p)
    {
        assertNonDispThread();

        enqueueIntoXmpp(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                _spf.peerConnected_(d, p);
            }
        }, Prio.LO);
    }

    @Override
    public void peerDisconnected(final DID d, final IPipe p)
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
                _spf.peerDisconnected_(d, p);
            }
        };

        if (Thread.currentThread() == _dispthr) {
            disconnectTask.handle_();
        } else {
            enqueueIntoXmpp(disconnectTask, Prio.LO);
        }
    }

    @Override
    public void processUnicastControl(final DID did, final PBTPHeader hdr)
    {
        assertNonDispThread();

        final PBTPHeader.Type type = hdr.getType();

        Prio evprio = Prio.LO;
        if (type == PBTPHeader.Type.TRANSPORT_CHECK_PULSE_CALL ||
            type == PBTPHeader.Type.TRANSPORT_CHECK_PULSE_REPLY) {
            evprio = Prio.HI;
        }

        enqueueIntoXmpp(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                try {
                    switch (type) {
                        case TRANSPORT_CHECK_PULSE_CALL:
                        case TRANSPORT_CHECK_PULSE_REPLY:
                            PBCheckPulse cp = hdr.getCheckPulse();
                            assert cp != null : ("invalid pulse msg from d:" + did);
                            processPulseControl_(did, cp, (type == TRANSPORT_CHECK_PULSE_CALL));
                            break;
                        case DIAGNOSIS:
                            PBTransportDiagnosis dg = hdr.getDiagnosis();
                            assert dg != null : ("invalid diagnosis from d:" + did);
                            processDiagnosis_(did, dg);
                            break;
                        default:
                            processUnicastControl_(did, hdr);
                            break;
                    }
                } catch (ExProtocolError e) {
                    assert false : ("unhandled control pkt from d:" + did + " type:" + hdr.getType().name());
                }
            }
        }, evprio);
    }

    @Override
    public void processUnicastPayload(final DID did, final PBTPHeader hdr, final ByteArrayInputStream bodyis, final int wirelen)
    {
        assertNonDispThread();

        enqueueIntoXmpp(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                try {
                    Endpoint ep = new Endpoint(XMPP.this, did);
                    PBTPHeader ret = TPUtil.processUnicastPayload(ep, hdr, bodyis, wirelen, _sink, _sm);
                    if (ret != null) sendControl_(did, ret, Prio.LO);
                } catch (Exception e) {
                    l.warn("could not respond to d:" + did + " for pkt:" + hdr.getType().name() + " err:" + e);
                }
            }
        }, Prio.LO);
    }

    @Override
    public void closePeerStreams(final DID did, final boolean outbound, final boolean inbound)
    {
        assertNonDispThread();

        enqueueIntoXmpp(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                TPUtil.sessionEnded(new Endpoint(XMPP.this, did), _sink, _sm, outbound, inbound);
            }
        }, Prio.HI);
    }

    //--------------------------------------------------------------------------
    //
    //
    // ISignallingChannel methods (how subsystems can send/receive messages via
    // a control channel)
    //
    //
    //--------------------------------------------------------------------------

    @Override
    public void registerSignallingClient_(PBTPHeader.Type type, ISignallingClient ccc)
    {
        assert !ready() : ("x: already started");
        assert !_processors.containsKey(type) : ("x: existing ccc for type:" + type.name());

        _processors.put(type, ccc);
    }

    @Override
    public void sendMessageOnSignallingChannel(
            final DID did, final PBTPHeader msg, final ISignallingClient ccc)
    {
        assertNonDispThread();

        enqueueIntoXmpp(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                OutArg<Integer> len = new OutArg<Integer>(0);
                String enc = XMPP.encodeBody(len, msg.toByteArray());

                final Message xmsg = new Message(ID.did2jid(did, false), Message.Type.normal);
                xmsg.setBody(enc);

                // for now we actually don't have to enqueue the sending task as a new
                // event since sendPacket() is synchronized, but it's easy to do so if we
                // have to for whatever reason (i.e. I've tried both approaches and this
                // signature supports both). This allows us to implement this method
                // however we wish, with whatever synchronization style we want

                try {
                    _cw.conn().sendPacket(xmsg);
                } catch (XMPPException e) {
                    notifySignallingClientOfError(e);
                } catch (IllegalStateException e) {
                    // NOTE: this can happen because smack considers it illegal to attempt to send
                    // a packet if the channel is not connected. Since we may be notified of a
                    // disconnection after actually enqueuing the packet to be sent, it's entirely
                    // possible for this to occur

                    notifySignallingClientOfError(e);
                }
            }

            private void notifySignallingClientOfError(Exception e)
            {
                try {
                    ccc.sendSignallingMessageFailed_(did, msg, e);
                } catch (ExNoResource e2) {
                    l.error("x: failed to handle error while sending packet");
                    SystemUtil.fatal("shutdown due to err:" + e2 + " triggered by err:" + e);
                }
            }
        }, Prio.HI);
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
        l.warn("x: cw noticed disconnect");

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
                    _sink.enqueueBlocking(new EIPresence(XMPP.this, false, null), Prio.LO);
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
            assertDispThread(); // prevent against future mistakes

            serverDisconnectedEvent.handle_(); // happened in the disp thr, so run directly
        } else {
            assertNonDispThread();

            enqueueIntoXmpp(serverDisconnectedEvent, Prio.HI);
        }
    }

    @Override
    public void xmppServerConnected(final XMPPConnection conn) throws XMPPException
    {
        assertNonDispThread();

        final PacketTypeFilter presfilter = new PacketTypeFilter(Presence.class);
        final MessageTypeFilter msgfilter = new MessageTypeFilter(Message.Type.normal);
        final PacketFilter fullfilter = new OrFilter(presfilter, msgfilter);

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
                        } else if (packet instanceof Message) {
                            // for now all non-presence packets are directed to Zephyr
                            // clients only

                            Message m = (Message) packet;
                            Message.Type t = m.getType();

                            if (m.getSubject() != null) return;

                            assert t != groupchat && t != headline && t != chat :
                                ("pl: groupchat, headline and chat messages are not expected here");
                            assert t != error :
                                ("pl: errors and headlines are unhandled here");

                            try {
                                processMessage_(m);
                            } catch (ExFormatError e) {
                                l.warn("pl: cannot convert JID to DID from:" +
                                    packet.getFrom() + " err: " +
                                    Util.e(e));
                            } catch (ExProtocolError e) {
                                l.warn("pl: unrecognized message from:" +
                                    packet.getFrom() + " err:" + Util.e(e));
                            } catch (Exception e) {
                                l.error("pl: cannot process valid message from:" +
                                    packet.getFrom() + " err:" + Util.e(e));

                                // we fatal for a number of reasons here:
                                // - an exception from disconnect_ within a subsystem
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
                        }
                    }
                }, Prio.LO);
            }
        }, fullfilter);

        if (_mobileConnector != null) {
            _mobileConnector.setConnection(conn);
        }

        enqueueIntoXmpp(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                try {
                    _spf.xmppServerConnected_();
                    _mc.xmppServerConnected(
                            null); // FIXME: should not pass in null - change Multicast to use passed-in connection
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
    private void enqueueIntoXmpp(IEvent ev, Prio pri)
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

    //--------------------------------------------------------------------------
    //
    //
    // Internal methods - should only be called within XMPP event-dispatch thread
    //
    //
    //--------------------------------------------------------------------------

    @Override
    public void disconnect_(DID did)
        throws ExNoResource
    {
        assertDispThread();

        _spf.disconnect_(did, new Exception("forced disconnect"));
    }

    @Override
    public void linkStateChanged_(
            Set<NetworkInterface> removed,
            Set<NetworkInterface> added,
            Set<NetworkInterface> prev,
            Set<NetworkInterface> current)
        throws ExNoResource
    {
        assertDispThread();

        _cw.linkStateChanged(removed, current);
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
     * cannot be processed by {@link SignalledPipeFanout} due to resource constraints
     * within an {@link ISignalledPipe}
     */
    private void processPresence_(Presence p)
        throws ExFormatError, ExNoResource
    {
        assertDispThread();

        // NOTE: if the device goes offline then _zm will catch this since
        // the TCP connection via Zephyr will break

        String[] tokens = ID.tokenize(p.getFrom());
        if (!ID.isMUCAddress(tokens)) return;

        SID sid = ID.muc2sid(tokens[0]);
        DID did = ID.user2did(tokens[1]);
        if (did.equals(Cfg.did())) return;

        if (p.isAvailable()) {
            _xpm.add(did, sid);
            _pm.stopPulse(did, false);
        } else {
            _spf.disconnect_(did, new Exception("remote offline"));
            boolean waslast = _xpm.del(did, sid);
            if (waslast) _pm.stopPulse(did, false);
        }

        _sink.enqueueBlocking(new EIPresence(this, p.isAvailable(), did, sid), Prio.LO);
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
    private void processUnicastControl_(DID did, PBTPHeader hdr)
        throws ExProtocolError
    {
        assertDispThread();

        PBTPHeader.Type type = hdr.getType();
        assert type != DATAGRAM && type != DIAGNOSIS : ("invalid hdr type:" + type.name());
        if (type == STREAM) {
            assert hdr.getStream().getType() != Type.PAYLOAD : ("invalid stream hdr type:" + hdr.getStream().getType());
        }

        Endpoint ep = new Endpoint(this, did);
        try {
            PBTPHeader ret = TPUtil.processUnicastControl(ep, hdr, _sink, _sm);
            sendControl_(did, ret, Prio.LO);
        } catch (ExNoResource e) {
            assert false : ("could not enqueue into core d:" + did); // FIXME: shouldn't we enqueue blocking into core?
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
    private void processPulseControl_(DID did, PBCheckPulse cp, boolean cpcall)
    {
        assertDispThread();

        int pulseid = cp.getPulseId();
        if (cpcall) {
            l.info("rcv pulse req msgpulseid:" + pulseid + " d:" + did);
            sendControl_(did, newCheckPulseReply(pulseid), Prio.HI);
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
    private void processDiagnosis_(DID did, PBTransportDiagnosis dg)
        throws ExProtocolError
    {
        assertDispThread();

        PBTransportDiagnosis dgret = processUnicastControlDiagnosis(did, dg, _spf, _tds);
        if (dgret != null) {
            PBTPHeader ret = makeDiagnosis(dgret);
            sendControl_(did, ret, Prio.LO);
        }
    }

    /**
     * Provides a uniform way to send control responses to a peer inside the
     * <code>XMPP</code> event-dispatch thread. It allows (and will not send)
     * a <code>null</code> packet and logs an exception thrown during the
     * <code>send_</code> call
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
     * be <code>null</code>, in which case <code>sendControl_</code> acts as a
     * no-op
     * @param pri {@link Prio} priority with which the message is scheduled for
     * sending
     */
    // FIXME: very similar to method in TCP that is used by many classes within IPacketController implementation - consider refactor
    private void sendControl_(DID did, @Nullable PBTPHeader hdr, Prio pri)
    {
        assertDispThread();

        if (hdr == null) {
            l.debug("null return");
            return;
        }

        try {
            _spf.send_(did, null, pri, newControl(hdr), null);
        } catch (Exception e) {
            l.warn("could not respond to d:" + did + " pkt:" + hdr.getType().name() + " err:" + e);
        }
    }

    /**
     * Processes an encoded XMPP message from a peer.
     *
     * @param m XMPP message to decode and process
     * @throws ExFormatError if the JID cannot be converted to a DID
     * @throws ExProtocolError if the header is an unrecognized (i.e. unhandled) type
     * @throws ExNoResource if the message can't be processed by an
     * {@link ISignallingClient} due to resource constraints
     */
    private void processMessage_(Message m) throws ExFormatError, ExProtocolError, ExNoResource
    {
        assertDispThread();

        DID did = ID.jid2did(m.getFrom());
        PBTPHeader hdr;
        try {
            OutArg<Integer> wirelen = new OutArg<Integer>(0);
            byte[] decoded = decodeBody(did, wirelen, m.getBody());
            if (decoded == null) return;
            hdr = PBTPHeader.parseFrom(decoded);
        } catch (IOException e) {
            l.warn(Util.e(e));
            return;
        }

        PBTPHeader.Type type = hdr.getType();
        l.debug("rcv msg type:" + hdr.getType().name());

        ISignallingClient mp = _processors.get(type);
        if (mp == null) throw new ExProtocolError(type.getClass());
        mp.processSignallingMessage_(did, hdr);
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

            os.writeInt(C.CORE_MAGIC);

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
        DataInputStream is = new DataInputStream(new Base64.InputStream(bos));
        try {
            int magic = is.readInt();
            if (magic != C.CORE_MAGIC) {
                l.warn("magic mismatch " +
                        "d:" + did + " exp:" + C.CORE_MAGIC + " act:" + magic + " bdy:" + body);

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
                throw new IOException("msg len " + len + " < avail by " +
                        bos.available());
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
    private void assertDispThread()
    {
        assert _dispthr != null : ("null disp thr");
        assert Thread.currentThread() == _dispthr : ("method called from non-disp thr");
    }

    /**
     * Asserts that the current method is <em>not</em> being called from within
     * the <code>XMPP</code> {@link EventDispatcher} thread*
     */
    private void assertNonDispThread()
    {
        assert _dispthr != null : ("null disp thr");
        assert Thread.currentThread() != _dispthr : ("method called from disp thr");
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
        _cw.dumpStatMisc(indent2, indentUnit, ps);
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

    private final IBlockingPrioritizedEventSink<IEvent> _sink;
    private final BlockingPrioQueue<IEvent> _q = new BlockingPrioQueue<IEvent>(QUEUE_LENGTH);
    private final Scheduler _sched = new Scheduler(_q, id());
    private final EventDispatcher _disp = new EventDispatcher();
    private Thread _dispthr;

    private final PulseManager _pm = new PulseManager();
    private final StreamManager _sm = new StreamManager();
    private final XMPPPresenceManager _xpm = new XMPPPresenceManager();    private final XMPPServerConnection _cw;

    private final MaxcastFilterReceiver _mcfr;
    private final SignalledPipeFanout _spf;
    private final Multicast _mc = new Multicast(this);

    private final TransportDiagnosisState _tds = new TransportDiagnosisState();

    private final Map<PBTPHeader.Type, ISignallingClient> _processors = new HashMap<PBTPHeader.Type, ISignallingClient>();

    private final MobileServerZephyrConnector _mobileConnector;

    private static final Logger l = Util.l(XMPP.class);

    //
    // constants
    //

    private final static int HEADER_LEN = (Integer.SIZE / Byte.SIZE) * 2 + 1;
    private final static int MAXCAST_UNFILTERED = -1;
}

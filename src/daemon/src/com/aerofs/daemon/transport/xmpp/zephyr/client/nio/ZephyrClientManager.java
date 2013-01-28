/**
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc. 2011.
 */

package com.aerofs.daemon.transport.xmpp.zephyr.client.nio;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.daemon.transport.lib.INetworkStats;
import com.aerofs.daemon.transport.lib.IPipeController;
import com.aerofs.daemon.transport.lib.TPUtil;
import com.aerofs.daemon.transport.xmpp.ISignalledPipe;
import com.aerofs.daemon.transport.xmpp.ISignallingChannel;
import com.aerofs.daemon.transport.xmpp.zephyr.client.nio.statemachine.CoreEvent;
import com.aerofs.daemon.transport.xmpp.zephyr.client.nio.statemachine.ExInvalidTransition;
import com.aerofs.daemon.transport.xmpp.zephyr.client.nio.statemachine.StateMachine;
import com.aerofs.daemon.transport.xmpp.zephyr.client.nio.statemachine.StateMachineEvent;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.ex.ExNoResource;
import com.aerofs.proto.Files.PBDumpStat;
import com.aerofs.proto.Files.PBDumpStat.PBTransport;
import com.aerofs.proto.Transport;
import com.aerofs.proto.Transport.PBTPHeader;
import com.aerofs.proto.Transport.PBZephyrCandidateInfo;
import com.aerofs.zephyr.core.BufferPool;
import com.aerofs.zephyr.core.FatalIOEventHandlerException;
import com.aerofs.zephyr.core.IIOEventHandler;
import com.aerofs.zephyr.core.ZUtil;
import org.apache.log4j.Logger;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEventType.SEL_CONNECT;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEventType.SEL_READ;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.ZephyrClientEventType.SEL_WRITE;
import static com.aerofs.daemon.transport.xmpp.zephyr.client.nio.statemachine.CoreEvent.HALT;
import static com.aerofs.proto.Transport.PBTPHeader.Type.ZEPHYR_CANDIDATE_INFO;

/**
 * Manages a set of connections to a Zephyr relay server. The methods
 * use the following conventions:
 *
 * <ul>
 * <li>DID = d</li>
 * <li>IResultWaiter = w</li>
 * <li>Prio = p</li>
 * <li>byte[][] = b</li>
 * <li>Object = o (strmCookie, aka c_o_okie)</li>
 * <li>ZephyrClientContext = c</li>
 * <li>Event = ev</li>
 * </ul>
 *
 * Unless explicitly specified, all references to a <code>DID <em>d</em></code>
 * refer to the <em>remote</em> <code>DID</code>, not the local one.
 *
 * - FIXME: remove SelectionKey from ZephyrClientContext
 */
public class ZephyrClientManager implements ISignalledPipe, IIOEventHandler
{
    /**
     * Constructor
     * @param id external name of <code>ZephyrClientManager</code>
     * @param rank how <code>ZephyrClientManager</code> should be ranked in a
     * set of {@link com.aerofs.daemon.transport.xmpp.IPipe} objects
     * @param pc {@link IPipeController} object that owns this ZephyrClientManager
     * <strong>XMPP object is the one into which we will enqueue outgoing
     * @param ns {@link INetworkStats} object in which bytes transmitted and
     * received via Zephyr will be stored
     * @param sc {@link ISignallingChannel} implementation of the out-of-band
     * signalling channel Zephyr will use to communicate <code>zid</code>, etc.
     * to remotes
     */
    public ZephyrClientManager(String id, int rank, IPipeController pc, INetworkStats ns, ISignallingChannel sc)
    {
        _bid = new BasicIdentifier(id, rank);
        _pc = pc;
        _ns = ns;
        _sc = sc;
        _disp = new ClientDispatcher();
        _dispthr = new Thread(_disp);
        _dispthr.setName("zcmd");
        _bufpool = new BufferPool(
            BufferPool.DEFAULT_BYTEBUFFER_SIZE,
            BufferPool.DEFAULT_INITIAL_NUM_BYTEBUFFERS);
        _dids = new HashMap<DID, SelectionKey>();
        _ctxs = new HashMap<SelectionKey, ZephyrClientContext>();
        _sm = new StateMachine<ZephyrClientContext>(ZephyrClientSpec.STATE_MACHINE_SPEC);
        _started = false;
    }

    //
    // starting
    //

    /**
     * Call to initialize the selector
     * @throws IOException if the selector cannot be initialized
     */
    @Override
    public void init_() throws IOException
    {
        _sc.registerSignallingClient_(ZEPHYR_CANDIDATE_INFO, this);
        _disp.init_();

        l.info("zm: inited");
    }

    /**
     * Call to start the selector
     */
    @Override
    public void start_()
    {
        _dispthr.start();
        _started = true;

        l.info("zm: started");
    }

    @Override
    public boolean ready()
    {
        return _started;
    }

    //
    // identification
    //

    @Override
    public String id()
    {
        return _bid.id();
    }

    @Override
    public int rank()
    {
        return _bid.rank();
    }

    @Override
    public boolean equals(Object o)
    {
        if (o instanceof ZephyrClientManager == false) return false;

        ZephyrClientManager zcm = (ZephyrClientManager) o;
        return _bid.equals(zcm._bid);
    }

    @Override
    public int hashCode()
    {
        return _bid.hashCode();
    }

    //
    // ISignalledPipe methods
    //

    @Override
    public void connect_(final DID d)
    {
        l.info("zm: d:" + d + " attempt connect");

        assertNonDispThread();

        if (!_started) return;

        enqueueIntoZephyr(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                try {
                    // phase 1 - see if there's someone who's hanging

                    {
                        SelectionKey k = _dids.get(d);
                        if (k != null) {
                            ZephyrClientContext c = getContext_(k);
                            if (c._conntoserver) {
                                l.warn("zm: d:" + d + " already connected");
                                return;
                            }

                            // an unconnected entry exists - kill it

                            cleanup_(c, k, new IOException("connect failed"));
                        }
                    }

                    // phase 2 - create a new client

                    ZephyrClientContext c = create_(d);
                    c.startup_();
                } catch (IOException e) {
                    l.warn("zm: d:" + d + " cannot create cli");
                }
            }
        }, Prio.HI);
    }

    /**
     * Disconnect a specific peer (peer does not necessarily have to be connected
     * to Zephyr)
     *
     * @param d {@link DID} of the peer whose connections should be terminated
     * @param ex Exception that will be delivered to waiters when discarding packets -
     * <strong>exception cannot be null!</strong>
     */
    @Override
    public void disconnect_(final DID d, final Exception ex)
    {
        l.info("zm: d:" + d + " attempt disconnect");

        assertNonDispThread();

        assert ex != null : "cannot disconnect with null exception";

        if (!_started) return;

        enqueueIntoZephyr(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                kill_(d, ex);
            }
        }, Prio.HI);
    }

    @Override
    public Object send_(final DID d, final IResultWaiter w, final Prio p, final byte[][] b, final Object o)
        throws Exception
    {
        assertNonDispThread();

        assert _started : ("zm: not started - cannot send");

        l.debug("zm: -> d:" + d + " p:" + p);

        enqueueIntoZephyr(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                try {
                    sendInternal_(d, w, p, b, o);
                } catch (Exception e) {
                    ZephyrClientUtil.handleError(d, w, e, l);
                }
            }
        }, p);

        return null;
    }

    //
    // generic network-related
    //

   @Override
    public void linkStateChanged_(final Set<NetworkInterface> rem, final Set<NetworkInterface> cur)
    {
        assertNonDispThread();

        if (!_started) return;

        if (cur.isEmpty()) {
            enqueueIntoZephyr(new AbstractEBSelfHandling()
            {
                @Override
                public void handle_()
                {
                    brutalkill_();
                }
            }, Prio.HI);
        } else {
            enqueueIntoZephyr(new AbstractEBSelfHandling()
            {
                @Override
                public void handle_()
                {
                    linksReconfigured_(rem, cur);
                }
            }, Prio.HI);
        }
    }

    @Override
    public void signallingChannelConnected_()
        throws ExNoResource
    {
        // this is a noop
    }

    @Override
    public void signallingChannelDisconnected_()
        throws ExNoResource
    {
        assertNonDispThread();

        enqueueIntoZephyr(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                brutalkill_();
            }
        }, Prio.HI);
    }

    @Override
    public void sendSignallingMessageFailed_(final DID d, PBTPHeader failedmsg, Exception failex)
        throws ExNoResource
    {
        assertNonDispThread();

        assert failedmsg.getType() == PBTPHeader.Type.ZEPHYR_CANDIDATE_INFO :
            ("zm: unexpected msg type on cc failure reported t:" + failedmsg.getType().name());

        l.warn("zm: fail send loczid:" + failedmsg.getZephyrInfo().getSourceZephyrId() +
            " to remote did:" + d + " err:" + failex);

        enqueueIntoZephyr(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                l.warn("zm: kill cli for did:" + d);
                kill_(d, new IOException("cannot connect via zepyhr to d:" + d));
            }
        }, Prio.HI);
    }

    //
    // IIoEventHandler methods
    //

    @Override
    public void handleConnectReady_(SelectionKey k)
        throws FatalIOEventHandlerException
    {
        assertDispThread();

        assert k.isValid(): ("zm: k" + k + " invalid");
        assert k.isConnectable() : ("zm: k:" + k + "not connectable");

        ZephyrClientContext c = getContext_(k);
        c.enqueue_(new StateMachineEvent(SEL_CONNECT));
        runClientStateMachine_(c);
    }

    @Override
    public void handleReadReady_(SelectionKey k)
        throws FatalIOEventHandlerException
    {
        assertDispThread();

        assert k.isValid(): ("zm: k" + k + " invalid");
        assert k.isReadable() : ("zm: k:" + k + " not readable");

        ZephyrClientContext c = getContext_(k);
        c.enqueue_(new StateMachineEvent(SEL_READ));
        runClientStateMachine_(c);
    }

    @Override
    public void handleWriteReady_(SelectionKey k)
        throws FatalIOEventHandlerException
    {
        assertDispThread();

        assert k.isValid(): ("zm: k" + k + " invalid");
        assert k.isWritable() : ("zm: k:" + k + "not writable");

        ZephyrClientContext c = getContext_(k);
        c.enqueue_(new StateMachineEvent(SEL_WRITE));
        runClientStateMachine_(c);
    }

    @Override
    public void handleKeyCancelled_(SelectionKey k)
        throws FatalIOEventHandlerException
    {
        assertDispThread();

        ZephyrClientContext c = _ctxs.get(k);
        if (c == null) return;

        cleanup_(c, k, null); // key cancellation happens b/c of i/o error
    }

    //
    // IPipeDebug methods
    //

    // FIXME (AG): refactor use of cvobj out

    @Override
    public long getBytesRx(final DID did)
    {
        assertNonDispThread();

        if (!_started) return -1;

        l.debug("zm: gbrx did:" + did);

        final OutArg<Long> bytes = new OutArg<Long>(-1L);

        try {
            final Object cvobj = new Object();

            synchronized (cvobj) {
                enqueueIntoZephyr(new AbstractEBSelfHandling()
                {
                    @Override
                    public void handle_()
                    {
                        synchronized (cvobj) {
                            try {
                                bytes.set(getBytesIn_(did));
                            } catch (ExInvalidZephyrClient e) {
                                l.warn("zm: fail gbrx for did:" + did);
                            } finally {
                                cvobj.notifyAll();
                            }
                        }
                    }
                }, Prio.HI); // don't want the caller to wait too long

                cvobj.wait();
            }
        } catch (InterruptedException e) {
            assert false : ("zm: interrupted during gbrx wait");
        }

        return bytes.get();
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
    {
        assertNonDispThread();

        l.debug("zm: dumpstatmisc");

        ps.println(indent + "disp:ready");
    }

    @Override
    public void dumpStat(PBDumpStat template, final PBDumpStat.Builder dsbuilder)
    {
        assertNonDispThread();

        l.debug("zm: dumpstat");

        final PBTransport tptemplate = template.getTp(0);
        assert tptemplate != null : ("zm: invalid dumpstat template");

        try {
            final Object cvobj = new Object();

            synchronized (cvobj) {
                enqueueIntoZephyr(new AbstractEBSelfHandling()
                {
                    @Override
                    public void handle_()
                    {
                        synchronized (cvobj) {
                            try {
                                dumpstat_(tptemplate, dsbuilder);
                            } finally {
                                cvobj.notifyAll();
                            }
                        }
                    }
                }, Prio.HI); // don't want the core to block too long

                cvobj.wait();
            }
        } catch (InterruptedException e) {
            assert false : ("zm: interrupted during dumpstat wait");
        }
    }

    /**
     * Method to be called from within <code>ZephyrClientManager</code> dispatcher
     * thread to fill in a <code>PBDumpStat</code> template with requested information
     *
     * @param tptemplate {@link PBTransport} template marking what information
     * should be populated into the supplied message builder. If a field is present
     * in the template it should be populated in the <code>dsbuilder</code> with the
     * correct value
     * @param dsbuilder {@link PBDumpStat} builder into which the actual transport
     * information should be populated
     */
    private void dumpstat_(PBTransport tptemplate, PBDumpStat.Builder dsbuilder)
    {
        assertDispThread();

        PBTransport.Builder tpbuilder = PBTransport.newBuilder();

        tpbuilder.setBytesIn(_ns.getBytesRx());
        tpbuilder.setBytesOut(_ns.getBytesTx());

        if (tptemplate.hasName()) {
            tpbuilder.setName(id());
        }

        if (tptemplate.getConnectionCount() != 0) {
            for (ZephyrClientContext c : _ctxs.values()) {
                tpbuilder.addConnection("zc[" + c.tinydesc() + "]");
            }
        }

        if (tptemplate.hasDiagnosis()) {
            String endl = System.getProperty("line.separator");

            StringBuilder strbuilder = new StringBuilder(1024);
            for (ZephyrClientContext c : _ctxs.values()) {
                strbuilder.append(c.hugedesc());
                strbuilder.append(endl);
            }

            tpbuilder.setDiagnosis(strbuilder.toString());
        }

        dsbuilder.addTp(tpbuilder);
    }

    //
    // sending/receiving events, channel ids and messages
    //

    /**
     * Call when a {@link org.jivesoftware.smack.PacketListener} receives a
     * message with a Zephyr ID from a remote ZephyrClient
     * @param d DID of the remote peer that sent us the Zephyr ID
     * @param msg {@link PBTPHeader} of type <code>ZEPHYR_CANDIDATE_INFO</code> -
     * <strong>asserts that message is of this type</strong>
     */
    @Override
    public void processSignallingMessage_(final DID d, final PBTPHeader msg)
        throws ExNoResource
    {
        l.info("zm: ->sig d:" + d);

        assertNonDispThread();

        if (!_started) return;

        assert msg.getType() == ZEPHYR_CANDIDATE_INFO :
            ("zm: unexpected trans hdr type:" + msg.getType());

        final PBZephyrCandidateInfo zi = msg.getZephyrInfo();
        assert zi != null : ("zm: null zi from d:" + d);

        if (!zi.hasDestinationZephyrId()) {
            l.warn("zm: ->sig d:" + d + " drop old signalling msg");
            return;
        }

        enqueueIntoZephyr(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                try {
                    processSignallingZids_(d, zi);
                } catch (IOException e) {
                    l.error("err processing channel id from peer:" + e);
                }
            }
        }, Prio.HI);
    }

    /**
     * Send the Zephyr ID for our connection to a remote ZephyrClient to that client
     * @param remotedid {@link com.aerofs.base.id.DID} of the remote ZephyrClient
     * @param localzid Zephyr ID of our connection to the Zephyr relay server
     * @param remotezid Zephyr ID of the endpoint being connected to
     */
    void sendZidToRemote_(final DID remotedid, final int localzid, int remotezid)
    {
        l.info("zm: <-zid:" + localzid + " remzid:" + remotezid + " d:" + remotedid);

        assertDispThread();

        PBTPHeader zci = PBTPHeader
            .newBuilder()
            .setType(ZEPHYR_CANDIDATE_INFO)
            .setZephyrInfo(PBZephyrCandidateInfo
                .newBuilder()
                .setSourceZephyrId(localzid)
                .setDestinationZephyrId(remotezid))
            .build();

        _sc.sendMessageOnSignallingChannel(remotedid, zci, this);
    }

    void remoteConnected_(DID d)
    {
        assertDispThread();

        _pc.closePeerStreams(d, true, true); // terminate sessions that may be lying around
        _pc.peerConnected(d, ConnectionType.WRITABLE, this); // FIXME (AG): actually both readable/writable
    }

    void remoteDisconnected_(DID d)
    {
        assertDispThread();

        _pc.closePeerStreams(d, true, true);
        _pc.peerDisconnected(d, this);
    }

    /**
     * Call to deliver a newly-received packet to the core
     *
     * @param d {@link DID} of the remote peer from whom eht message was received
     * @param bais {@link java.io.ByteArrayInputStream} holding the received data
     * @param wirelen number of bytes this packet took up on the wire (including all heders)
     * @throws Exception if there is an error in delivery
     */
    void deliver_(DID d, ByteArrayInputStream bais, int wirelen)
        throws Exception
    {
        assertDispThread();

        Transport.PBTPHeader transhdr = TPUtil.processUnicastHeader(bais);
        if (TPUtil.isPayload(transhdr)) {
            _pc.processUnicastPayload(d, transhdr, bais, wirelen);
        } else {
            _pc.processUnicastControl(d, transhdr);
        }
    }

    /**
     * Call to indicate that we received num bytes
     * @param bytes number of bytes received
     */
    void addBytesRx_(int bytes)
    {
        assertDispThread();

        _ns.addBytesRx(bytes);
    }

    /**
     * Call to indicate that we transmitted num bytes
     * @param bytes number of bytes transmitted
     */
    void addBytesTx_(int bytes)
    {
        assertDispThread();

        _ns.addBytesTx(bytes);
    }

    /**
     * Call when we want to unPARK (i.e. unpause and run again) the state machine
     * for a certain ZephyrClient
     * @param c {@link ZephyrClientContext} object representing that ZephyrClient
     */
    void reschedule_(final ZephyrClientContext c)
    {
        assertDispThread();

        enqueueIntoZephyr(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {

                runClientStateMachine_(c);
            }
        }, Prio.LO);
    }

    //
    // getters and setters
    //

    /**
     * @return the buffer pool from which all buffer allocations should be performed
     */
    BufferPool getBufferPool()
    {
        assertDispThread();

        return _bufpool;
    }

    //
    // utility
    //

    /**
     * Run the state machine for a certain ZephyrClient
     * @param c {@link ZephyrClientContext} object representing that ZephyrClient
     */
    private void runClientStateMachine_(ZephyrClientContext c)
    {
        assertDispThread();

        //
        // this case can happen if we enqueue an event (ev-a) and
        // between the time we process that event (ev-a), another
        // event (ev-b) _already_ in the event queue causes the
        // ZephyrClientContext to terminate. since we cannot flush
        // the ZephyrClientManager event queue we have to recognize
        // that the context is terminated and simply skip processing
        // ev-a
        //
        // an alternative is to empty the event queue and explicitly
        // put in a HALT, but that seems a little hackish, and
        // will result in the entire cleanup process being run again
        //

        if (c.terminated_()) {
            l.warn("zm: skip sm run for terminated zc:" + c);
            return;
        }

        assert (_dids.containsKey(c._remdid)) :
            ("zm: no dids entry, but zc not marked as terminated zc: " + c);

        assert (_dids.get(c._remdid) == c._k) :
            ("zm: key cancelled, but zc not marked as terminated zc: " + c);

        try {
            CoreEvent lastev = _sm.run_(c);
            if (lastev == HALT) cleanup_(c, c._k, null); // ok to use null - explicit HALT implies ex (invalid transitions handled in catch block below)
        } catch (ExInvalidTransition e) {
            assert false : ("invalid transition");
        } catch (Exception e) {
            l.error("zm: sm fail: " + c + " err:" + e);
            cleanup_(c, c._k, e);
        }
    }

    private void processSignallingZids_(DID remotedid, PBZephyrCandidateInfo zi)
        throws IOException
    {
        assertDispThread();

        ZephyrClientContext c = null;
        SelectionKey k = _dids.get(remotedid);
        if (k != null) {
            l.info("zm: ->sig[srczid:" + zi.getSourceZephyrId() + " dstzid:" + zi.getDestinationZephyrId() + " d:" + remotedid + "] cli old");
            c = getContext_(k);
            c.processSignallingZids_(zi);
        } else {
            assert !_dids.containsKey(remotedid) : ("zm: exists cli for did:" + remotedid);

            l.info("zm: ->sig[srczid:" + zi.getSourceZephyrId() + " dstzid:" + zi.getDestinationZephyrId() + " d:" + remotedid + "] cli new");
            c = create_(remotedid);
            c.processSignallingZids_(zi);
            c.startup_(); // new client, so explicit startup required
        }
    }

    private void sendInternal_(DID d, IResultWaiter w, Prio p, byte[][] b, Object o)
        throws IOException, ExNoResource, ExInvalidZephyrClient
    {
        assertDispThread();

        SelectionKey k = _dids.get(d);

        if (k == null) throw new ExInvalidZephyrClient("zm: no connection", d);
        assert k.isValid() : ("zm: should never contain invalid key"); // special condition to track down if this happens

        ZephyrClientContext c = getContext_(k);
        c.enqueueSegment_(w, p, b, o);
    }

    /**
     * Create a {@link ZephyrClientContext} for a connection to a remote ZephyrClient
     * with {@link DID} (<strong>also sets up our internal maps</strong>)
     * @param d {@link DID} for which to create the ZephyrClientContext object
     * @return constructed and initialized ZephyrCLientContext object
     * @throws IOException if we cannot register with the
     * {@link java.nio.channels.Selector} for i/o notification
     */
    private ZephyrClientContext create_(DID d)
        throws IOException
    {
        assertDispThread();

        SocketChannel sc = SocketChannel.open();
        sc.configureBlocking(false);
        sc.socket().setTcpNoDelay(true); // disable nagle
        sc.socket().setSoLinger(true, 0); // attempt to close immediately

        SelectionKey k = _disp.register(sc, this);
        ZephyrClientContext c = new ZephyrClientContext(Cfg.did(), d, k, this);
        addContext_(d, k, c);

        if (l.isDebugEnabled()) {
            l.info("zm: created k:" + k + " for " + c);
        } else {
            l.info("zm: created k for " + c);
        }

        return c;
    }

    private void linksReconfigured_(Set<NetworkInterface> rem, Set<NetworkInterface> cur)
    {
        assertDispThread();

        l.warn("zm: link reconf");

        Set<InetAddress> goneips = new HashSet<InetAddress>();

        // build up a list of removed inet addresses

        InetAddress a;
        for (NetworkInterface n : rem) {
            Enumeration<InetAddress> addrs = n.getInetAddresses(); // when does the SecurityManager throw an error for checkConnect?
            while (addrs.hasMoreElements()) {
                a = addrs.nextElement();
                l.debug("zm: link reconf rem addr:" + a);
                goneips.add(a);
            }
        }

        // now disconnect any connections whose source ip is in that set

        SocketChannel sc;
        Set<DID> gonedids = new HashSet<DID>();
        for (Map.Entry<DID, SelectionKey> e : _dids.entrySet()) {
            sc = ZUtil.getSocketChannel(e.getValue());
            a = sc.socket().getLocalAddress();
            if (goneips.contains(a)) {
                l.debug("zm: link reconf kills did:" + e.getKey());
                gonedids.add(e.getKey());
            }
        }

        for (DID d : gonedids) kill_(d, new IOException("link down"));
    }

    /**
     * Kills all connections to Zephyr and cleans up all their associated
     * {@link ZephyrClientContext} objects
     */
    private void brutalkill_()
    {
        assertDispThread();

        l.warn("zm: brutal kill");

        Set<DID> dids = new HashSet<DID>(_dids.keySet());
        for (DID d : dids) kill_(d, new IOException("zm general disconnect"));
    }

    /**
     * Kill the connection to Zephyr for a DID
     * @param d {@link DID} whose connection we want to disconnect
     * @param e <code>Exception</code> to deliver to the <code>Core</code> to explain
     * the disconnection
     */
    private void kill_(DID d, Exception e)
    {
        assertDispThread();

        assert d != null : ("zm: null did");

        SelectionKey k = _dids.get(d);
        if (k == null) return;

        ZephyrClientContext c = getContext_(k);
        cleanup_(c, k, e);
    }

    private void cleanup_(ZephyrClientContext c, SelectionKey k, @Nullable Exception e)
    {
        assertDispThread();

        assert c != null : ("zm: null cli");
        assert k != null : ("zm: null key");

        l.debug("zm: cln: " + c);

        subContext_(c._remdid);
        c.cleanup_(e);
        ZUtil.closeChannel(k.channel()); // closing channel can _only_ take place after we've done all internal cleanup actions

        remoteDisconnected_(c._remdid);

        assert !_ctxs.containsKey(k) :
            ("zm: exist ctx for did:" + c._remdid);
        assert !_dids.containsKey(c._remdid) :
            ("zm: exist key for did:" + c._remdid);
    }

    private long getBytesIn_(DID d)
        throws ExInvalidZephyrClient
    {
        assertDispThread();

        l.debug("zm: gbi did:" + d);

        if (!_dids.containsKey(d)) throw new ExInvalidZephyrClient("Invalid client", d);

        SelectionKey k = _dids.get(d);
        assert _ctxs.containsKey(k) : ("zm: no cli for key:" + k + " did:" + d);

        ZephyrClientContext c = _ctxs.get(k);
        return c._bytesrx;
    }

    private void addContext_(DID d, SelectionKey k, ZephyrClientContext c)
    {
        assertDispThread();

        // this can happen if re-registering with the selector
        // this in and of itself this isn't a bug, but it's a logical bug for us
        assert !_dids.containsKey(d) : ("zm: contains key for did: " + d);
        assert !_ctxs.containsKey(k) : ("zm: contains cli:" + _ctxs.get(k) + "for key:" + k); // shouldn't happen if the above passes, no?

        _dids.put(d, k);
        _ctxs.put(k, c);
    }

    private void subContext_(DID d)
    {
        assertDispThread();

        if (!_dids.containsKey(d)) return;

        SelectionKey k = _dids.remove(d);
        assert k != null : ("zm: no key for did:" + d);

        ZephyrClientContext c = _ctxs.remove(k);
        assert c != null : ("zm: no cli for key:" + k + " for did:" + d);

        l.debug("zm: rem: " + c + " from maps");
    }

    private ZephyrClientContext getContext_(SelectionKey k)
    {
        assertDispThread();

        ZephyrClientContext c = _ctxs.get(k);
        assert c != null : ("zm: invalid cli for key:" + k);

        SelectionKey kact = _dids.get(c._remdid); // reverse check
        assert kact != null && kact == k :
            ("zm: mismatched keys expected:" + kact + " actual:" + k);

        return c;
    }

    /**
     * Enqueue an event into the {@link ClientDispatcher} event queue to be handled later
     * @param event to be handled
     * @param prio priority of the event
     * for handling (this means that this event cannot be handled!)
     */
    private void enqueueIntoZephyr(AbstractEBSelfHandling event, Prio prio)
    {
        assert _started : ("zm: uninited - cannot enqueue");

        _disp.enqueue(event, prio);
    }

    /**
     * Asserts that the method is running in the <code>ZephyrClientManager</code>
     * event-dispatch thread (managed by {@link ClientDispatcher}
     */
    void assertDispThread()
    {
        Thread curthr = Thread.currentThread();
        assert curthr == _dispthr : ("zm: unexpected thr:" + curthr.getName());
    }

    /**
     * Asserts that the method is running in the <code>ZephyrClientManager</code>
     * event-dispatch thread (managed by {@link ClientDispatcher}
     */
    void assertNonDispThread()
    {
        Thread curthr = Thread.currentThread();
        assert curthr != _dispthr : ("zm: unexpected thr:" + curthr.getName());
    }

    //
    // unused selector physical event handlers
    //

    @Override
    public void handleAcceptReady_(SelectionKey k)
        throws FatalIOEventHandlerException
    {
        assert false : ("zm: does not handle accept()");
    }

    //
    // members
    //

    /** Our identifier */
    private final BasicIdentifier _bid;

    /** {@link com.aerofs.daemon.transport.lib.IPipeController} object that owns us */
    private final IPipeController _pc;

    /** {@link INetworkStats} object to which we should add tx/rx counts */
    private final INetworkStats _ns;

    /** {@link com.aerofs.daemon.transport.xmpp.ISignallingChannel} via which we send out-of-band control messages */
    private final ISignallingChannel _sc;

    /** {@link ClientDispatcher} that will notify us of i/o events */
    private final ClientDispatcher _disp;

    /** Dispatcher thread */
    private final Thread _dispthr;

    /** {@link BufferPool} from which all {@link java.nio.ByteBuffer} objects are allocated/retrieved */
    private final BufferPool _bufpool;

    /**
     * INVARIANT: if d is in _dids , then there must be a _non_null_ k such that
     * k is _also present_ in _ctxs and has an associated, _non_null_ d
     *
     * All the active connections to Zephyr (if a DID is here it is active and we
     * have a SelectionKey and ZephyrClientContext object for it)
     */
    private final Map<DID, SelectionKey> _dids;

    /**
     * Given a {@link SelectionKey}, this is the {@link ZephyrClientContext}
     * that represents that connection to Zephyr
     */
    private final Map<SelectionKey, ZephyrClientContext> _ctxs;

    /** {@link StateMachine} initialized with the transition map for ZephyrClient operation */
    private final StateMachine<ZephyrClientContext> _sm;

    /** indicates whether the system has been initialized */
    private volatile boolean _started;

    /** logger */
    private static Logger l = com.aerofs.lib.Util.l(ZephyrClientManager.class);
}

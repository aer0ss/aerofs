package com.aerofs.daemon.transport.xmpp.jingle;

import com.aerofs.base.BaseParam.Xmpp;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.JabberID;
import org.apache.log4j.Logger;

import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.transport.lib.INetworkStats;
import com.aerofs.daemon.transport.lib.IPipeController;
import com.aerofs.daemon.transport.xmpp.ISignalledPipe;
import com.aerofs.daemon.transport.xmpp.XMPP;
import com.aerofs.j.Jid;
import com.aerofs.lib.InOutArg;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExJingle;
import com.aerofs.lib.ex.ExNoResource;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.Files;
import com.aerofs.proto.Files.PBDumpStat.PBTransport;
import com.aerofs.proto.Transport.PBTPHeader;

import java.io.ByteArrayInputStream;
import java.io.PrintStream;
import java.net.NetworkInterface;
import java.util.Set;

/**
 *  message format
 *
 * +--------------+-----+------+
 * | MAGIC_NUMBER | len | data |
 * +--------------+-----+------+
 */

public class Jingle implements ISignalledPipe, IJingle
{
    public Jingle(String id, int rank, IPipeController pc, INetworkStats ns)
    {
        OSUtil.get().loadLibrary("aerofsj");

        bid = new BasicIdentifier(id, rank);
        this.pc = pc;
        this.ns = ns;

        _st = new SignalThread(this);
        _st.setDaemon(true);
        _st.setName(SIGNAL_THREAD_THREAD_ID);
    }

    //
    // startup
    //

    @Override
    public void init_()
        throws Exception
    {
        // empty
    }

    @Override
    public void start_()
    {
      _st.start();
    }

    @Override
    public boolean ready()
    {
        return _st.ready();
    }

    //
    // identification
    //

    @Override
    public String id()
    {
        return bid.id();
    }

    @Override
    public int rank()
    {
        return bid.rank();
    }

    @Override
    public boolean equals(Object o)
    {
        if (o instanceof Jingle == false) return false;

        Jingle j = (Jingle)o;
        return bid.equals(j.bid);
    }

    @Override
    public int hashCode()
    {
        return bid.hashCode();
    }

    //
    // connection/disconnection
    //

    @Override
    public void connect_(final DID did)
    {
        l.info("j: queue into st: connect d:" + did);

        _st.call(new ISignalThreadTask()
        {
            @Override
            public void run()
            {
                Engine eng = _st.getEngine_();
                if (eng != null && !eng.isClosed_()) {
                    eng.connect_(did);
                } else {
                    error(new ExJingle("j: engine closed for connect"));
                }
            }

            @Override
            public void error(Exception e)
            {
                l.warn("j: fail connect for d:" + did + " err:" + e);
                pc.peerDisconnected(did, Jingle.this);
            }

            @Override
            public String toString()
            {
                return "connect d:" + did;
            }
        });
    }

    @Override
    // AAG FIXME: should I call disconnect manually on an error?
    public void disconnect_(final DID did, final Exception e)
    {
        l.warn("j: queue into st: disconnect cause: " + e + " d:" + did);

        _st.call(new ISignalThreadTask()
        {
            @Override
            public void run()
            {
                _st.close_(did, e);
            }

            @Override
            public void error(Exception e)
            { /* silently ignore */ }

            @Override
            public String toString()
            {
                return "disconnect cause: " + e + " d:" + did;
            }
        });
    }

    // AAG FIXME: I suspect there's an issue if this happens when connections are in progress
    private void disconnectAll_()
    {
        l.warn("j: queue into st: disconnect all dids");

        _st.call(new ISignalThreadTask()
        {
            @Override
            public void run()
            {
                _st.close_(new ExJingle("disconnect all")); // st reconnects after close_ called
            }

            @Override
            public void error(Exception e)
            { /* silently ignore */ }

            @Override
            public String toString()
            {
                return "disconnect all dids";
            }
        });
    }

    private void connectionStateChanged(boolean up)
    {
        disconnectAll_();
        _st.linkStateChanged(up);
    }

    @Override
    public void linkStateChanged_(Set<NetworkInterface> rem, Set<NetworkInterface> cur)
    {
        boolean up = !XMPP.allLinksDown(cur);
        l.debug("j: lsc link up?:" + up);

        connectionStateChanged(up);
    }

    @Override
    public void processSignallingMessage_(DID did, PBTPHeader msg)
        throws ExNoResource
    {
        assert false : ("did not register to receive messages on signalling channel");
    }

    @Override
    public void sendSignallingMessageFailed_(DID did, PBTPHeader failedmsg, Exception failex)
        throws ExNoResource
    {
        assert false : ("did not send messages via default signalling channel");
    }

    @Override
    public void signallingChannelConnected_()
    {
        connectionStateChanged(true);
    }

    @Override
    public void signallingChannelDisconnected_()
    {
        connectionStateChanged(false);
    }

    /**
     * N.B. this method may be called within the signal thread
     */
    @Override
    public Object send_(final DID did, final IResultWaiter waiter, final Prio pri, final byte[][] bss, Object cke)
    {
        _st.call(new ISignalThreadTask()
        {
            @Override
            public void run()
            {
                Engine eng = _st.getEngine_();
                if (eng != null && !eng.isClosed_()) {
                    eng.send_(did, bss, pri, wtr);
                } else {
                    error(new ExJingle("engine closed"));
                }
            }

            @Override
            public void error(Exception e)
            {
                l.warn("fail send pkt: d:" + did + " err: " + e);
                if (wtr != null) wtr.error(e);
            }

            @Override
            public String toString()
            {
                return "send: d:" + did;
            }

            private final IResultWaiter wtr = waiter;
        });

        return null;
    }

    //
    // various pass-throughs through to an IPipeController instance
    // IMPORTANT: Since these methods implement those in the IJingle
    // interface I cannot make them package private (which I'd have liked to do)
    // to work around this I will simply assert that they are called within the
    // signal thread so that this assumption is enforced
    //
    // To be clear:
    // DO NOT CALL METHODS IN THE IJINGLE INTERFACE OUTSIDE OF THE SIGNAL THREAD!
    //

    @Override
    public void addBytesRx(long bytesrx)
    {
        _st.assertThread();

        ns.addBytesRx(bytesrx);
    }

    @Override
    public void addBytesTx(long bytestx)
    {
        _st.assertThread();

        ns.addBytesTx(bytestx);
    }

    @Override
    public void peerConnected(DID did)
    {
        _st.assertThread();

        pc.peerConnected(did, ConnectionType.WRITABLE, this);
    }

    @Override
    public void peerDisconnected(DID did)
    {
        _st.assertThread();

        pc.peerDisconnected(did, this);
    }

    @Override
    public void processUnicastControl(DID did, PBTPHeader hdr)
    {
        _st.assertThread();

        pc.processUnicastControl(did, hdr);
    }

    @Override
    public void processUnicastPayload(DID did, PBTPHeader hdr, ByteArrayInputStream bodyis, int wirelen)
    {
        _st.assertThread();

        pc.processUnicastPayload(did, hdr, bodyis, wirelen);
    }

    @Override
    public void closePeerStreams(DID did, boolean outbound, boolean inbound)
    {
        _st.assertThread();

        pc.closePeerStreams(did, outbound, inbound);
    }

    //
    // IPipeDebug methods
    //

    @Override
    public long getBytesRx(final DID did)
    {
        final InOutArg<Long> ret = new InOutArg<Long>(0L);

        _st.call(new ISignalThreadTask()
        {
            @Override
            public void run()
            {
                Engine eng = _st.getEngine_();
                if (eng != null) {
                    ret.set(eng.getBytesIn_(did));
                } else {
                    ret.set(0L);
                }
            }

            @Override
            public void error(Exception e)
            {
               ret.set(-1L); // is this really necessary? useful to flag error vs. closed case
            }

            @Override
            public String toString()
            {
                return "gbrx";
            }
        });

        return ret.get();
    }

    @Override
    public void dumpStat(final Files.PBDumpStat template, final Files.PBDumpStat.Builder bdbuilder)
        throws Exception
    {
        final PBTransport tp = template.getTp(0);
        assert tp != null : ("called dumpstat on transport will null tp");

        // set default fields

        final PBTransport.Builder tpbuilder = PBTransport.newBuilder();
        tpbuilder.setBytesIn(ns.getBytesRx());
        tpbuilder.setBytesOut(ns.getBytesTx());

        // set a default for the diagnosis

        if (tp.hasDiagnosis()) {
            tpbuilder.setDiagnosis("call not executed");
        }

        _st.call(new ISignalThreadTask()
        {
            @Override
            public void run()
            {
                if (tp.hasName()) tpbuilder.setName(id());

                if (tp.getConnectionCount() != 0) {
                    for (DID c : _st.getConnections_()) {
                        tpbuilder.addConnection(c.toString());
                    }
                }

                if (tp.hasDiagnosis()) {
                    tpbuilder.setDiagnosis(_st.diagnose_());
                }

                bdbuilder.addTp(tpbuilder);
            }

            @Override
            public void error(Exception e)
            {
                l.warn("cannot dumpstat err:" + e); // hmm...using Jingle's logger, not SignalThread's
            }

            @Override
            public String toString()
            {
                return "dumpstat";
            }
        });
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
    {
        // ideally we want to do an _st.call, but in this _particular_ case
        // we don't have to because all it's doing is checking if a reference
        // is null

        _st.dumpStatMisc(indent, indentUnit, ps);
    }

    //
    // utility methods
    //

    /**
     * Convert a <code>DID</code> to an XMPP <code>JID</code> valid on the
     * AeroFS XMPP server
     *
     * @param did {@link DID} to convert
     * @return a valid XMPP user id of the form: {$user}@{$domain}/{$resource}
     */
    static Jid did2jid(DID did)
    {
        return new Jid(JabberID.did2user(did), Xmpp.SERVER_DOMAIN, JINGLE_RESOURCE_NAME);
    }

    static DID jid2did(Jid jid) throws ExFormatError
    {
        return JabberID.user2did(jid.node());
    }

    //
    //
    // members

    private final BasicIdentifier bid;
    private IPipeController pc;
    private final INetworkStats ns;
    private final SignalThread _st;

    private static final Logger l = Util.l(Jingle.class);

    private static final String SIGNAL_THREAD_THREAD_ID = "st";
    private static final String JINGLE_RESOURCE_NAME = "u";
}

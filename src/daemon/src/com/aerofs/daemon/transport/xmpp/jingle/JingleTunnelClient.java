package com.aerofs.daemon.transport.xmpp.jingle;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.event.lib.imc.IResultWaiter;
import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.lib.LRUCache;
import com.aerofs.daemon.lib.LRUCache.IEvictionListener;
import com.aerofs.j.Jid;
import com.aerofs.j.SWIGTYPE_p_cricket__Session;
import com.aerofs.j.StreamInterface;
import com.aerofs.j.TunnelSessionClient;
import com.aerofs.j.TunnelSessionClient_IncomingTunnelSlot;
import com.aerofs.j.XmppMain;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.ex.ExJingle;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map.Entry;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Design rationales:
 *
 * 1. we cache two streams for each peer device to handle the case when
 * the pair initiates connections at the same time. if only one stream is
 * maintained for each peer, the outgoing stream has to be canceled by
 * the incoming stream, and therefore both connections would fail.
 *
 * 2. closePeerStreams is called when a) receiving a new incoming stream, and
 * b) when both primary and secondary streams have closed. A is needed to
 * prevent security attackers from reusing existing streams,
 *
 * 3. when sending, we always select the younger stream (Tandem._ps[0]) as
 * the older one (Tandem._ps[1]) might have been stale/disconnected.
 *
 * 4. when receiving a new incoming stream, we destroy the old incoming
 * stream from the same peer while keeping the outgoing stream.
 *
 * ----
 *
 * Code convention for the jingle package:
 *
 * 1. a class must implement IProxyObjectContainer if it contains SWIG proxy
 * objects. see the interface's source code for more comments
 *
 * 2. a class that contains IProxyObjectContainer objects but doesn't implement
 * the interface itself must call the containing objects' delete() whenever
 * they are no longer used.
 */

// N.B. all the methods of this class must be called within the signal thread
// including dumpStat()
//
public class JingleTunnelClient implements IProxyObjectContainer
{
    private static final Logger l = Loggers.getLogger(JingleTunnelClient.class);

    private final TunnelSessionClient_IncomingTunnelSlot _slotIncomingTunnel = new TunnelSessionClient_IncomingTunnelSlot()
    {
        @Override
        public void onIncomingTunnel(TunnelSessionClient client, Jid jid, String desc, SWIGTYPE_p_cricket__Session sess)
        {
            onIncomingTunnel_(client, jid, sess);
        }
    };

    private final JingleDataStream.IClosureListener _cclosl = new JingleDataStream.IClosureListener()
    {
        @Override
        public void closed_(JingleDataStream jds)
        {
            l.debug("eng: ccl triggered close jds:" + jds + " d:" + jds.did());

            _st.delayedDelete_(jds);

            DID did = jds.did();

            Tandem t = _cache.get_(did);
            checkNotNull(t);
            t.remove_(jds);

            if (t.isEmpty_()) {
                l.debug("eng: remove tandem d:" + did);

                _cache.invalidate_(did);

                // since the tandem itself is empty we signal that both incoming and
                // outgoing streams are closed
                // also, see comments at top of Engine.java
                _ij.closePeerStreams(did, true, true);

                // A. case where channel is shut down because of an error
                // have to signal here, because we don't know when eviction runs
                _ij.peerDisconnected(did);

                l.debug("eng: make ccl peer disconnected callback d:" + did);
            }
        }
    };

    private final JingleDataStream.IConnectionListener _cconl = new JingleDataStream.IConnectionListener()
    {
        @Override
        public void connected_(JingleDataStream jingleDataStream)
        {
            l.debug("eng: connected jds:" + jingleDataStream + " d:" + jingleDataStream.did());

            DID did = jingleDataStream.did();
            Tandem t = _cache.get_(did);
            assert t != null : ("null tandem for " + jingleDataStream);
            t.connected_();
        }
    };

    // TODO: This should be consolidated with the device LRU in the core
    private final LRUCache<DID, Tandem> _cache = new LRUCache<DID, Tandem>(DaemonParam.deviceLRUSize(), new IEvictionListener<DID, Tandem>()
    {
        @Override
        public void evicted_(DID did, Tandem t)
        {
            l.debug("eng: t:" + t + " d:" + did + " evicted");
            close_(did, t, new ExJingle("evicted"));
        }
    });

    private final IJingle _ij;
    private final SignalThread _st;
    private final TunnelSessionClient _tsc;

    private boolean _closed;

    JingleTunnelClient(IJingle ij, XmppMain main, SignalThread st)
    {
        this._ij = ij;
        this._tsc = new TunnelSessionClient(main.xmpp_client().jid(), main.session_manager());
        this._slotIncomingTunnel.connect(_tsc);
        this._st = st;
    }

    private void add_(DID did, JingleDataStream p)
    {
        _st.assertThread();
        assert !_closed;

        Tandem t = _cache.get_(did);
        if (t == null) {
            t = new Tandem(_ij);
            _cache.put_(did, t);
        }
        t.add_(p);
    }

    private void onIncomingTunnel_(TunnelSessionClient client, Jid jid, SWIGTYPE_p_cricket__Session sess)
    {
        _st.assertThread();
        assert !_closed;

        DID did;
        try {
            did = Jingle.jid2did(jid);
        } catch (ExFormatError e) {
            l.warn("eng: incoming connection w/ bogus id. decline: " + jid.Str());
            client.DeclineTunnel(sess);
            return;
        }

        StreamInterface s = client.AcceptTunnel(sess);
        l.debug("eng: new channel d:" + did);
        JingleDataStream jingleDataStream = new JingleDataStream(_ij, s, did, true, _cclosl, _cconl);
        add_(did, jingleDataStream);

        // see the comments on top
        _ij.closePeerStreams(did, true, true);
    }

    boolean isClosed_()
    {
        return _closed;
    }

    void connect_(final DID did)
    {
        _st.assertThread();
        assert !_closed;

        Tandem t = _cache.get_(did);
        JingleDataStream jingleDataStream = t != null ? t.get_() : null;

        if (jingleDataStream == null) {
            Jid jid = Jingle.did2jid(did);
            StreamInterface s = _tsc.CreateTunnel(jid, "a");
            l.debug("eng: create channel to d:" + did);
            jingleDataStream = new JingleDataStream(_ij, s, did, false, _cclosl, _cconl);
            add_(did, jingleDataStream);
        }

        l.info("eng: connect initiated to d:" + did);
    }

    void send_(final DID did, byte[][] bss, Prio prio, IResultWaiter waiter)
    {
        _st.assertThread();
        assert !_closed;

        Tandem t = _cache.get_(did);
        JingleDataStream jingleDataStream = t != null ? t.get_() : null;

        //
        // IMPORTANT: it is possible to end up with a null channel because of a change in
        // the way the engine is called. Previously send_ was a combination of:
        //
        // 1) connect if no tandem/channel existed
        // 2) send
        //
        // now this has been split into two explicit steps because we need coordination between
        // the packet-routing layer and the actual transport mechanism layer. Because of this it's
        // possible that there will be send events _still in the XMPP event queue_ after a
        // disconnect event occurs within jingle. (note: actually, this happens regardless of
        // whether packet routing is used or not - basically, between the time a disconnect event
        // occurs and the sending thread is notified of the disconnection, the event queue can be
        // filled with outgoing packets). Because send events are still in the xmpp event
        // queue and are being serviced, they _will_ make it. Instead of asserting, we should simply
        // log it, notify waiters, and the upper layer will find out about the disconnection in
        // time (presumably they were notified via _ij.peerDisconnected(did))
        //

        if (jingleDataStream == null) {
            l.warn("eng: null chan d:" + did);
            if (waiter != null) waiter.error(new Exception("null chan")); // explicitly handle err
            return;
        }

        jingleDataStream.send_(bss, prio, waiter);
    }

    void close_(Exception e)
    {
        l.warn("eng: didless close: cause: " + e);

        _st.assertThread();

        Iterator<Entry<DID, Tandem>> iter = _cache.iterator_();
        while (iter.hasNext()) {
            Entry<DID, Tandem> en = iter.next();
            close_(en.getKey(), en.getValue(), e);
        }

        _cache.invalidateAll_();

        _closed = true;
    }

    /**
     * close the tandem but don't remove it from the cache
     *
     * @param did {@link DID} of the remote peer for whom we should close the tandem
     * @param t {@link Tandem} we are interested in closing
     * @param e Exception to deliver to waiters (describing reason for close)
     */
    private void close_(DID did, Tandem t, Exception e)
    {
        _st.assertThread();

        if (t.isConnected_()) _ij.closePeerStreams(did, true, true);
        t.close_(e);

        //
        // we get to this point for _all_ aerofs-initiated shutdowns.
        // these include:
        //
        // 1. explicit disconnections for a single peer
        // 2. explicit disconnections for _all_ peers that are triggered by link-state changes,
        //    xmpp-server disconnections, etc.
        //
        // it is crucial that we notify the upper layers that this disconnection occurred,
        // otherwise their view of the world is out of sync with libjingle's and they will keep
        // attempting to send packets via a connection/state that doesn't exist. this triggers
        // the call too long bug because the underlying connection is no longer inside the
        // libjingle library
        //

        l.debug("eng: make peer disconnected callback d:" + did);
        _ij.peerDisconnected(did); // B. signal for aerofs-initiated shutdowns
    }

    /**
     * close the tandem and remove it from the cache
     * request ignored if the tandem doesn't exist for the given peer
     *
     * @param did {@link DID} of the remote peer for whom we should close the tandem
     * @param e Exception to deliver to waiters (describing reason for close)
     */
    void close_(DID did, Exception e)
    {
        _st.assertThread();

        l.warn("eng: close tandem: cause: " + e + " d:" + did);

        Tandem t = _cache.get_(did);
        if (t == null) return;

        //
        // IMPORTANT: DO NOT CALL _ij.peerDisconnected(did) HERE!!
        // it will be called by close(did, t, e) above!
        //

        close_(did, t, e);
        _cache.remove_(did);
    }

    @Override
    public void delete_()
    {
        _st.assertThread();
        assert _closed;

        _tsc.delete();
        _slotIncomingTunnel.delete();
    }

    long getBytesIn_(DID did)
    {
        _st.assertThread();

        Tandem t = _cache.get_(did);
        return t == null ? 0 : t.getBytesIn_();
    }

    String diagnose_()
    {
        _st.assertThread();

        Iterator<Entry<DID, Tandem>> iter = _cache.iterator_();
        StringBuilder sb = new StringBuilder();
        while (iter.hasNext()) {
            sb.append(iter.next().getValue().diagnose_());
        }

        return sb.toString();
    }

    Collection<DID> getConnections_()
    {
        _st.assertThread();

        ArrayList<DID> ret = new ArrayList<DID>();
        Iterator<Entry<DID, Tandem>> iter = _cache.iterator_();
        while (iter.hasNext()) ret.add(iter.next().getKey());
        return ret;
    }
}

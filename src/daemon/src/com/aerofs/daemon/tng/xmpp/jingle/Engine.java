/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.tng.xmpp.jingle;

import com.aerofs.daemon.lib.DaemonParam;
import com.aerofs.daemon.lib.LRUCache;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.daemon.lib.LRUCache.IEvictionListener;
import com.aerofs.j.Jid;
import com.aerofs.j.SWIGTYPE_p_cricket__Session;
import com.aerofs.j.StreamInterface;
import com.aerofs.j.TunnelSessionClient;
import com.aerofs.j.TunnelSessionClient_IncomingTunnelSlot;
import com.aerofs.j.XmppMain;
import com.aerofs.lib.Util;
import com.aerofs.lib.async.UncancellableFuture;
import com.aerofs.lib.ex.ExFormatError;
import com.aerofs.lib.ex.ExJingle;
import com.aerofs.lib.id.DID;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map.Entry;

/**
 * Design rationales:
 * <p/>
 * 1. we cache two streams for each peer device to handle the case when the pair initiates
 * connections at the same time. if only one stream is maintained for each peer, the outgoing stream
 * has to be canceled by the incoming stream, and therefore both connections would fail.
 * <p/>
 * 2. closePeerStreams is called when a) receiving a new incoming stream, and b) when both primary
 * and secondary streams have closed. A is needed to prevent security attackers from reusing
 * existing streams,
 * <p/>
 * 3. when sending, we always select the younger stream (Tandem._ps[0]) as the older one
 * (Tandem._ps[1]) might have been stale/disconnected.
 * <p/>
 * 4. when receiving a new incoming stream, we destroy the old incoming stream from the same peer
 * while keeping the outgoing stream.
 * <p/>
 * ----
 * <p/>
 * Code convention for the jingle package:
 * <p/>
 * 1. a class must implement IProxyObjectContainer if it contains SWIG proxy objects. see the
 * interface's source code for more comments
 * <p/>
 * 2. a class that contains IProxyObjectContainer objects but doesn't implement the interface itself
 * must call the containing objects' delete() whenever they are no longer used.
 */

// N.B. all the methods of this class must be called within the signal thread
// including dumpStat()
//
final class Engine implements IProxyObjectContainer
{
    private static final Logger l = Util.l(Engine.class);

    private final TunnelSessionClient_IncomingTunnelSlot _slotIncomingTunnel = new TunnelSessionClient_IncomingTunnelSlot()
    {
        @Override
        public void onIncomingTunnel(TunnelSessionClient client, Jid jid, String desc,
                SWIGTYPE_p_cricket__Session sess)
        {
            onIncomingTunnel_(client, jid, sess);
        }
    };

    private final Channel.IClosureListener _cclosl = new Channel.IClosureListener()
    {
        @Override
        public void closed_(Channel c)
        {
            l.info("channel " + c + " closed");
            _st.delayedDelete_(c);

            DID did = c.did();
            Tandem t = _cache.get_(did);
            t.remove_(c);
            if (t.isEmpty_()) {
                l.info("remove tandem " + did);
                _cache.invalidate_(did);
                // see comments at top
                ij.closePeerStreams(did, true, true);
                // A. case where channel is shut down because of an error
                // have to signal here, because we don't know when eviction runs
                ij.peerDisconnected(did);
            }
        }
    };

    private final Channel.IConnectionListener _cconl = new Channel.IConnectionListener()
    {
        @Override
        public void connected_(Channel c)
        {
            l.info("channel " + c + " connected");

            DID did = c.did();
            Tandem t = _cache.get_(did);
            assert t != null : ("null tandem for " + c);
            t.connected_();
        }
    };

    // TODO: This should be consolidated with the device LRU in the core
    private final LRUCache<DID, Tandem> _cache = new LRUCache<DID, Tandem>(
            DaemonParam.deviceLRUSize(), new IEvictionListener<DID, Tandem>()
    {
        @Override
        public void evicted_(DID did, Tandem t)
        {
            l.info("tandem " + did + " evicted");
            close_(did, t, new ExJingle("evicted"));
        }
    });

    private final IJingle ij;
    private final SignalThread _st;
    private final TunnelSessionClient _tsc;

    private boolean _closed;

    Engine(IJingle ij, XmppMain main, SignalThread st)
    {
        this.ij = ij;
        _tsc = new TunnelSessionClient(main.getXmppClient().jid(), main.getSessionManager());
        _slotIncomingTunnel.connect(_tsc);

        _st = st;
    }

    private void add_(DID did, Channel p)
    {
        _st.assertThread();
        assert !_closed;

        Tandem t = _cache.get_(did);
        if (t == null) {
            t = new Tandem(did, ij);
            _cache.put_(did, t);
        }
        t.add_(p);
    }

    private void onIncomingTunnel_(final TunnelSessionClient client, Jid jid,
            final SWIGTYPE_p_cricket__Session sess)
    {
        _st.assertThread();
        assert !_closed;

        final DID did;
        try {
            did = JingleUnicastConnectionService.jid2did(jid);
        } catch (ExFormatError e) {
            l.warn("incoming connection w/ bogus id. decline: " + jid.Str());
            client.DeclineTunnel(sess);
            return;
        }

        // Create the task that must run once the incoming connection event
        // is fired
        ISignalThreadTask acceptTask = new ISignalThreadTask()
        {
            @Override
            public void run()
            {
                _st.assertThread();
                assert !_closed;

                StreamInterface s = client.AcceptTunnel(sess);
                l.info("create_ channel from " + did);
                Channel c = new Channel(ij, s, did, true, _cclosl, _cconl);
                add_(did, c);

                // see the comments on top
                ij.closePeerStreams(did, true, true);
            }

            @Override
            public void error(Exception e)
            {

            }
        };

        // Notify Jingle of an incoming connection. The acceptTask will be run
        // on the SignalThread once Jingle accepts the connection
        ij.incomingConnection(did, acceptTask);
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
        Channel c = t != null ? t.get_() : null;

        if (c == null) {
            Jid jid = JingleUnicastConnectionService.did2jid(did);
            StreamInterface s = _tsc.CreateTunnel(jid, "a");
            l.info("eng: create_ channel to " + did);
            c = new Channel(ij, s, did, false, _cclosl, _cconl);
            add_(did, c);
        }
    }

    void send_(final DID did, byte[][] bss, Prio prio, UncancellableFuture<Void> future)
    {
        _st.assertThread();
        assert !_closed;

        Tandem t = _cache.get_(did);
        Channel c = t != null ? t.get_() : null;
        assert c != null : ("eng: null chan");

        c.send_(bss, prio, future);
    }

    void close_(Exception e)
    {
        l.info("close engine: " + Util.e(e, ExJingle.class));

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

        if (t.isConnected_()) ij.closePeerStreams(did, true, true);
        t.close_(e);
    }

    /**
     * close the tandem and remove it from the cache request ignored if the tandem doesn't exist for
     * the given peer
     *
     * @param did {@link DID} of the remote peer for whom we should close the tandem
     * @param e Exception to deliver to waiters (describing reason for close)
     */
    void close_(DID did, Exception e)
    {
        _st.assertThread();

        l.info("close tandem " + did + ": " + Util.e(e, ExJingle.class));

        Tandem t = _cache.get_(did);
        if (t == null) return;

        close_(did, t, e);
        _cache.remove_(did);
        ij.peerDisconnected(did); // B. signal for system-initiated shutdowns
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
        while (iter.hasNext()) { ret.add(iter.next().getKey()); }
        return ret;
    }
}

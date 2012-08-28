package com.aerofs.daemon.core.net;

import com.aerofs.daemon.core.CoreDeviceLRU;
import com.aerofs.daemon.core.IDeviceEvictionListener;
import com.aerofs.daemon.core.net.IncomingStreams.StreamKey;
import com.aerofs.daemon.core.net.proto.ComputeHashCall;
import com.aerofs.daemon.core.net.proto.Diagnosis;
import com.aerofs.daemon.core.net.proto.GetComponentCall;
import com.aerofs.daemon.core.net.proto.GetRevision;
import com.aerofs.daemon.core.net.proto.GetVersCall;
import com.aerofs.daemon.core.net.proto.ListRevChildren;
import com.aerofs.daemon.core.net.proto.ListRevHistory;
import com.aerofs.daemon.core.net.proto.NewUpdates;
import com.aerofs.daemon.core.net.proto.UpdateSenderFilter;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExBadCredential;
import com.aerofs.lib.ex.ExDeviceOffline;
import com.aerofs.lib.ex.ExProtocolError;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.proto.Core.PBCore;
import com.aerofs.proto.Core.PBCore.Type;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.util.Map;

public class UnicastInputTopLayer implements IUnicastInputLayer
{
    private static final Logger l = Util.l(UnicastInputTopLayer.class);

    public static class Factory
    {
        private final NSL _nsl;
        private final DID2User _d2u;
        private final RPC _rpc;
        private final GetComponentCall _pgcc;
        private final NewUpdates _pnu;
        private final GetVersCall _pgvc;
        private final UpdateSenderFilter _pusf;
        private final GetRevision _gr;
        private final ListRevChildren _rlc;
        private final ListRevHistory _rlh;
        private final Diagnosis _diag;
        private final ComputeHashCall _computeHashCall;
        private final IncomingStreams _iss;
        private final CoreDeviceLRU _dlru;

        @Inject
        public Factory(IncomingStreams iss, ComputeHashCall computeHashCall, Diagnosis diag, ListRevHistory rlh,
                ListRevChildren rlc, GetRevision gr, UpdateSenderFilter pusf, GetVersCall pgvc,
                NewUpdates pnu, GetComponentCall pgcc, RPC rpc, DID2User d2u, NSL nsl,
                CoreDeviceLRU dlru)
        {
            _iss = iss;
            _computeHashCall = computeHashCall;
            _diag = diag;
            _rlh = rlh;
            _rlc = rlc;
            _gr = gr;
            _pusf = pusf;
            _pgvc = pgvc;
            _pnu = pnu;
            _pgcc = pgcc;
            _rpc = rpc;
            _d2u = d2u;
            _nsl = nsl;
            _dlru = dlru;
        }

        public UnicastInputTopLayer create_()
        {
            return new UnicastInputTopLayer(this);
        }
    }

    private final Factory _f;

    // a cache for the DID2UserDatabase to avoid DB lookup on every incoming maxcast message.
    private final Map<DID, String> _d2uCache = Maps.newHashMap();

    private UnicastInputTopLayer(Factory f)
    {
        _f = f;
        _f._dlru.addEvictionListener_(new IDeviceEvictionListener() {
            @Override
            public void evicted_(DID did)
            {
                _d2uCache.remove(did);
            }
        });
    }

    @Override
    public void onUnicastDatagramReceived_(RawMessage r, PeerContext pc)
    {
        try {
            assert !pc.did().equals(Cfg.did());

            // TODO: make this able to deal with a fragmented PBCore
            // it's silly that upper layers have to guarantee that the content PBCore is smaller
            // than the transport's max unicast packet size; that's the job of a network layer
            PBCore pb = PBCore.parseDelimitedFrom(r._is);
            _f._nsl.recvUnicast_(pc.ep(), pb, null);

            DigestedMessage msg = new DigestedMessage(pb, r._is, pc.sidx(), pc.ep(), null,
                    pc.user());
            process_(msg);

        } catch (Exception e) {
            l.warn("process uc: " + Util.e(e,
                    ExDeviceOffline.class, ExBadCredential.class));
        }
    }

    public void maxcastMessageReceived_(SIndex sidx, Endpoint ep, ByteArrayInputStream is)
            throws Exception
    {
        assert !ep.did().equals(Cfg.did());

        PBCore pb = PBCore.parseDelimitedFrom(is);
        _f._nsl.recvMaxcast_(ep, pb);

        String user = _d2uCache.get(ep.did());
        if (user == null) {
            user = _f._d2u.getFromLocalNullable_(ep.did());
            if (user == null) user = _f._d2u.getFromPeer_(ep.did(), sidx);
            _d2uCache.put(ep.did(), user);
        }

        process_(new DigestedMessage(pb, is, sidx, ep, null, user));
    }

    private void process_(DigestedMessage msg)
            throws Exception
    {
        switch (msg.pb().getType()) {
            case REPLY:
                _f._rpc.processReply_(msg);
                break;
            case GET_COM_CALL:
                _f._pgcc.processCall_(msg);
                break;
            case NEW_UPDATES:
                _f._pnu.process_(msg);
                break;
            case GET_VERS_CALL:
                _f._pgvc.processCall_(msg);
                break;
            case UPDATE_SENDER_FILTER:
                _f._pusf.process_(msg);
                break;
            case GET_REVISION_CALL:
                _f._gr.processCall_(msg);
                break;
            case LIST_REV_CHILDREN_REQUEST:
                _f._rlc.processRequest_(msg);
                break;
            case LIST_REV_CHILDREN_RESPONSE:
                _f._rlc.processResponse_(msg);
                break;
            case LIST_REV_HISTORY_REQUEST:
                _f._rlh.processRequest_(msg);
                break;
            case LIST_REV_HISTORY_RESPONSE:
                _f._rlh.processResponse_(msg);
                break;
            case DIAGNOSIS:
                _f._diag.process_(msg);
                break;
            case COMPUTE_HASH_CALL:
                _f._computeHashCall.processCall_(msg);
                break;
            case NOP:
                break;
            default:
                l.warn("unkown msg: " + msg.pb().getType());
                throw new ExProtocolError(Type.class);
        }
    }

    @Override
    public void onStreamBegun_(StreamID streamId, RawMessage r, PeerContext pc)
    {
        try {
            StreamKey key = new StreamKey(pc.ep().did(), streamId);
            _f._iss.begun_(key, pc);

            PBCore pb = PBCore.parseDelimitedFrom(r._is);
            _f._nsl.recvUnicast_(pc.ep(), pb, key._strid);

            DigestedMessage msg = new DigestedMessage(pb, r._is, pc.sidx(), pc.ep(), key,
                    pc.user());

            switch (pb.getType()) {
                case REPLY:
                    if (!_f._rpc.processReply_(msg)) _f._iss.end_(key);
                    break;
                default:
                    l.warn("unkown msg 4 strm: " + msg.pb().getType());
                    throw new ExProtocolError(Type.class);
            }
        } catch (Exception e) {
            l.warn("process strm head: " + Util.e(e));
        }
    }

    @Override
    public void onStreamChunkReceived_(StreamID streamId, int seq, RawMessage r, PeerContext pc)
    {
        StreamKey key = new StreamKey(pc.ep().did(), streamId);
        _f._iss.processChunk_(key, seq, r._is);
    }

    @Override
    public void onStreamAborted_(StreamID streamId, Endpoint ep, InvalidationReason reason)
    {
        StreamKey key = new StreamKey(ep.did(), streamId);
        _f._iss.aborted_(key, reason);
    }

    @Override
    public void sessionEnded_(Endpoint ep, boolean outbound, boolean inbound)
    {
        // because stream management is mostly done by the transport layer,
        // we don't abort streams here but wait for the transport to signal us
        // for stream abortion. See TPUtil.sessionEnded()

        // Also, it's dangerous to do clear state associated with the session
        // in this method. Because incoming events from transports are at low
        // priority, including EISessionEnded, state associated with a "healthy"
        // session may be incorrectly destroyed due to what I call
        // "priority inversion". Consider the following case:
        //
        // 1. There was a session for a particular peer, which just ended
        // 2. A high priority request from FSI results in a new session
        //    established with the same peer. Both the core and the transport
        //    now save some state regarding this session (the event from core
        //    to transport is at high priority).
        // 3. The low-priority sessionEnd event generated in step 1 eventually
        //    arrived (after the FSI event above), causing the state of the new
        //    session destroyed.
        //
    }
}

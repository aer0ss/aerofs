/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.CoreDeviceLRU;
import com.aerofs.daemon.core.CoreUtil;
import com.aerofs.daemon.core.IDeviceEvictionListener;
import com.aerofs.daemon.core.net.IncomingStreams.StreamKey;
import com.aerofs.daemon.core.protocol.ComputeHashCall;
import com.aerofs.daemon.core.protocol.GetComponentCall;
import com.aerofs.daemon.core.protocol.GetVersCall;
import com.aerofs.daemon.core.protocol.NewUpdates;
import com.aerofs.daemon.core.protocol.UpdateSenderFilter;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExDeviceOffline;
import com.aerofs.proto.Core.PBCore;
import com.aerofs.proto.Core.PBCore.Type;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.io.InputStream;
import java.util.Map;

import static com.google.common.base.Preconditions.checkState;

public class UnicastInputTopLayer implements IUnicastInputLayer
{
    private static final Logger l = Loggers.getLogger(UnicastInputTopLayer.class);

    public static class Factory
    {
        private final NSL _nsl;
        private final DID2User _d2u;
        private final RPC _rpc;
        private final GetComponentCall _pgcc;
        private final NewUpdates _pnu;
        private final GetVersCall _pgvc;
        private final UpdateSenderFilter _pusf;
        private final ComputeHashCall _computeHashCall;
        private final IncomingStreams _iss;
        private final CoreDeviceLRU _dlru;

        @Inject
        public Factory(IncomingStreams iss, ComputeHashCall computeHashCall, UpdateSenderFilter pusf,
                GetVersCall pgvc, NewUpdates pnu, GetComponentCall pgcc, RPC rpc, DID2User d2u,
                NSL nsl, CoreDeviceLRU dlru)
        {
            _iss = iss;
            _computeHashCall = computeHashCall;
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
    private final Map<DID, UserID> _d2uCache = Maps.newHashMap();

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
            final DID did = pc.ep().did();

            checkState(!did.equals(Cfg.did()));

            // Note: in the past, the DTLS layer used to call processMappingFromPeer_() upon
            // successful handshake so that we could save in the db the mapping between did and
            // user id. However, with the removal of DTLS, this call would have to come from the
            // transport, which is cumbersome. So we do it here instead.
            if (_d2uCache.get(did) == null) {
                _d2uCache.put(did, pc.user());
                _f._d2u.processMappingFromPeer_(did, pc.user());
            }

            PBCore pb = PBCore.parseDelimitedFrom(r._is);
            DigestedMessage msg = new DigestedMessage(pb, r._is, pc.ep(), pc.user(), null);
            process_(msg);
        } catch (Exception e) {
            SystemUtil.fatalOnUncheckedException(e);
            l.warn("process uc: " + Util.e(e, ExDeviceOffline.class, ExBadCredential.class));
        }
    }

    public void maxcastMessageReceived_(Endpoint ep, InputStream is)
    {
        try {
            assert !ep.did().equals(Cfg.did());

            PBCore pb = PBCore.parseDelimitedFrom(is);
            UserID userId = _d2uCache.get(ep.did());
            if (userId == null) {
                userId = _f._d2u.getFromLocalNullable_(ep.did());
                if (userId == null) userId = _f._d2u.getFromPeer_(ep.did());
                _d2uCache.put(ep.did(), userId);
            }
            process_(new DigestedMessage(pb, is, ep, userId, null));
        } catch (Exception e) {
            SystemUtil.fatalOnUncheckedException(e);
            l.warn("process mc: " + Util.e(e, ExDeviceOffline.class, ExBadCredential.class));
        }
    }

    //
    // FIXME (AG): pull out process_* into a CoreMessageReactor
    //

    private void process_(DigestedMessage msg)
            throws Exception
    {
        // [sigh] I hate having to switch _twice_ on the type

        switch (msg.pb().getType()) {
        case GET_COM_CALL:
            //noinspection fallthrough
        case GET_VERS_CALL:
            //noinspection fallthrough
        case COMPUTE_HASH_CALL:
            try {
                processCall_(msg);
            } catch (Exception e) {
                // Don't use Util.e(e) to avoid spamming log files
                l.warn("process " + CoreUtil.typeString(msg.pb()) + ": " + e);
                sendErrorReply_(msg, e);
            }
            break;
        case REPLY:
            //noinspection fallthrough
        case NEW_UPDATES:
            //noinspection fallthrough
        case UPDATE_SENDER_FILTER:
            //noinspection fallthrough
        case NOP:
            processNonCall_(msg);
            break;
        default:
            l.warn("unkown msg: " + msg.pb().getType());
            throw new ExProtocolError(Type.class);
        }
    }

    private void processCall_(DigestedMessage msg)
            throws Exception
    {
        switch (msg.pb().getType()) {
        case GET_COM_CALL:
            _f._pgcc.processCall_(msg);
            break;
        case GET_VERS_CALL:
            _f._pgvc.processCall_(msg);
            break;
        case COMPUTE_HASH_CALL:
            _f._computeHashCall.processCall_(msg);
            break;
        default:
            // the caller guarantees it never happens
            throw new AssertionError(msg.pb().getType());
        }
    }

    private void sendErrorReply_(DigestedMessage msg, Exception cause)
            throws Exception
    {
        PBCore error = null;
        try {
            error = CoreUtil.newErrorReply(msg.pb(), cause);
        } catch (ExProtocolError e) {
            // this is a programming error: we shouldn't be creating error replies for non-rpc messages
            SystemUtil.fatal("fail creating a reply for msg:" + CoreUtil.typeString(msg.pb()));
        }

        assert error != null;
        _f._nsl.sendUnicast_(msg.did(), error);
    }

    private void processNonCall_(DigestedMessage msg)
            throws Exception
    {
        switch (msg.pb().getType()) {
        case REPLY:
            _f._rpc.processReply_(msg);
            break;
        case NEW_UPDATES:
            _f._pnu.process_(msg);
            break;
        case UPDATE_SENDER_FILTER:
            _f._pusf.process_(msg);
            break;
        case NOP:
            break;
        default:
            // the caller guarantees it never happens
            throw new AssertionError(msg.pb().getType());
        }
    }

    @Override
    public void onStreamBegun_(StreamID streamId, RawMessage r, PeerContext pc)
    {
        try {
            StreamKey key = new StreamKey(pc.ep().did(), streamId);
            _f._iss.begun_(key, pc);

            PBCore pb = PBCore.parseDelimitedFrom(r._is);
            DigestedMessage msg = new DigestedMessage(pb, r._is, pc.ep(), pc.user(), key);

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
        _f._iss.onAbortBySender_(key, reason);
    }
}

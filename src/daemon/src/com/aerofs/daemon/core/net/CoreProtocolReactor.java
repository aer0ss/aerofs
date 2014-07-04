/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.base.ex.ExTimeout;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.collector.ExNoComponentWithSpecifiedVersion;
import com.aerofs.daemon.core.net.IncomingStreams.StreamKey;
import com.aerofs.daemon.core.protocol.ComputeHash;
import com.aerofs.daemon.core.protocol.CoreProtocolUtil;
import com.aerofs.daemon.core.protocol.GetComponentRequest;
import com.aerofs.daemon.core.protocol.GetVersionsRequest;
import com.aerofs.daemon.core.protocol.GetVersionsResponse;
import com.aerofs.daemon.core.protocol.NewUpdates;
import com.aerofs.daemon.core.protocol.UpdateSenderFilter;
import com.aerofs.daemon.core.store.ExSIDNotFound;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExDeviceOffline;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.proto.Core.PBCore;
import com.aerofs.proto.Transport.PBStream.InvalidationReason;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.io.InputStream;

import static com.google.common.base.Preconditions.checkState;

public class CoreProtocolReactor implements IUnicastInputLayer
{
    private static final Logger l = Loggers.getLogger(CoreProtocolReactor.class);

    public static class Factory
    {
        private final TransportRoutingLayer _trl;
        private final DeviceToUserMapper _d2u;
        private final RPC _rpc;
        private final GetComponentRequest _pgcc;
        private final NewUpdates _pnu;
        private final GetVersionsRequest _pgvc;
        private final GetVersionsResponse _pgvr;
        private final UpdateSenderFilter _pusf;
        private final ComputeHash _computeHash;
        private final IncomingStreams _iss;

        // FIXME (AG): this wiring is terrible, inverse dep and allow handlers to auto-register?
        @Inject
        public Factory(
                IncomingStreams iss,
                ComputeHash computeHash,
                UpdateSenderFilter pusf,
                GetVersionsRequest pgvc,
                GetVersionsResponse pgvr,
                NewUpdates pnu,
                GetComponentRequest pgcc,
                RPC rpc,
                DeviceToUserMapper d2u,
                TransportRoutingLayer trl)
        {
            _iss = iss;
            _computeHash = computeHash;
            _pusf = pusf;
            _pgvc = pgvc;
            _pnu = pnu;
            _pgcc = pgcc;
            _pgvr = pgvr;
            _rpc = rpc;
            _d2u = d2u;
            _trl = trl;
        }

        public CoreProtocolReactor create_()
        {
            return new CoreProtocolReactor(this);
        }
    }

    private final Factory _f;

    private CoreProtocolReactor(Factory f)
    {
        _f = f;
    }

    @Override
    public void onUnicastDatagramReceived_(RawMessage r, PeerContext pc)
    {
        DID did = pc.ep().did();

        try {
            checkState(!did.equals(Cfg.did()));
            _f._d2u.onUserIDResolved_(did, pc.user());
            PBCore pb = PBCore.parseDelimitedFrom(r._is);
            processIncomingMessage_(new DigestedMessage(pb, r._is, pc.ep(), pc.user(), null), false);
        } catch (Exception e) {
            l.warn("{} fail process uc", did, LogUtil.suppress(e, ExDeviceOffline.class, ExBadCredential.class));
            SystemUtil.fatalOnUncheckedException(e);
        }
    }

    public void onMaxcastMessageReceived_(Endpoint ep, InputStream is)
    {
        DID did = ep.did();

        try {
            checkState(!did.equals(Cfg.did()));

            PBCore pb = PBCore.parseDelimitedFrom(is);
            UserID userID = _f._d2u.getUserIDForDIDNullable_(did);
            if (userID == null) {
                // FIXME (AG): This should be a queue of DID -> pending multicast packets
                // This would ensure that only the first thread waits
                // Other threads would place their multicast packet into the queue
                // and when the UserID is resolved the original thread can
                // process all pending messages
                userID = _f._d2u.issuePeerToPeerResolveUserIDRequest_(did);
            }

            processIncomingMessage_(new DigestedMessage(pb, is, ep, userID, null), true);
        } catch (Exception e) {
            l.warn("{} fail process mc", did,
                    LogUtil.suppress(e, ExTimeout.class, ExDeviceOffline.class, ExBadCredential.class, ExSIDNotFound.class));
            SystemUtil.fatalOnUncheckedException(e);
        }
    }

    private void processIncomingMessage_(DigestedMessage msg, boolean isMulticast)
            throws Exception
    {
        l.debug("{} <- {} {},{} over {}", msg.did(), isMulticast ? "mc" : "uc", CoreProtocolUtil.typeString(msg.pb()), msg.pb().getRpcid(), msg.tp());

        switch (msg.pb().getType()) {
        case GET_COMPONENT_REQUEST:
            try {
                _f._pgcc.processRequest_(msg);
            } catch (Exception e) {
                sendErrorResponse_(msg, e);
            }
            break;
        case COMPUTE_HASH_REQUEST:
            try {
                _f._computeHash.processRequest_(msg);
            } catch (Exception e) {
                sendErrorResponse_(msg, e);
            }
            break;
        case REPLY:
            _f._rpc.processResponse_(msg);
            break;
        case GET_VERSIONS_REQUEST:
            _f._pgvc.processRequest_(msg);
            break;
        case GET_VERSIONS_RESPONSE:
            _f._pgvr.processResponse_(msg);
            break;
        case NEW_UPDATES:
            _f._pnu.process_(msg);
            break;
        case UPDATE_SENDER_FILTER:
            _f._pusf.process_(msg);
            break;
        case RESOLVE_USER_ID_REQUEST:
            _f._d2u.respondToPeerToPeerResolveUserIDRequest_(msg.did());
            break;
        case RESOLVE_USER_ID_RESPONSE:
            // noop - this incoming message results in _d2u being updated in onUnicastDatagramReceived_
            break;
        default:
            l.warn("{} unknown message type {}", msg.did(), msg.pb().getType());
            throw new IllegalArgumentException("unhandled protocol type " + msg.pb().getType().name());
        }
    }

    private void sendErrorResponse_(DigestedMessage msg, Exception cause)
            throws Exception
    {
        l.warn("{} fail process msg cause:{}", msg.did(), CoreProtocolUtil.typeString(msg.pb()),
                BaseLogUtil.suppress(cause, ExNoComponentWithSpecifiedVersion.class));

        PBCore error = null;
        try {
            error = CoreProtocolUtil.newErrorResponse(msg.pb(), cause);
        } catch (ExProtocolError e) {
            // logic error
            // should not send an error response for non-rpc messages
            // FIXME (AG): does that mean that we often fail silently? seems bad...
            SystemUtil.fatal("fail creating a reply for msg:" + CoreProtocolUtil.typeString(msg.pb()));
        }

        checkState(error != null);

        _f._trl.sendUnicast_(msg.ep(), error);
    }

    // FIXME (AG): route this into processIncomingMessage_ above
    // FIXME (AG): have this class handle buffering data into the stream instead of pushing the responsibility to the reactors
    // FIXME (AG): makes an assumption that the first chunk contains a complete PB message
    // will ensure that all messages can now be streamed (which is the right thing to do)
    @Override
    public void onStreamBegun_(StreamID streamId, RawMessage r, PeerContext pc)
    {
        StreamKey key = new StreamKey(pc.ep().did(), streamId);
        try {
            _f._iss.begun_(key, pc);

            PBCore pb = PBCore.parseDelimitedFrom(r._is);
            DigestedMessage msg = new DigestedMessage(pb, r._is, pc.ep(), pc.user(), key);

            l.debug("{} <- uc {},{} over {} via {}", msg.did(), CoreProtocolUtil.typeString(msg.pb()), msg.pb().getRpcid(), msg.tp(), streamId);

            switch (pb.getType()) {
                case REPLY:
                    if (!_f._rpc.processResponse_(msg)) _f._iss.end_(key);
                    break;
                case GET_VERSIONS_RESPONSE:
                    _f._pgvr.processResponse_(msg);
                    break;
                default:
                    l.warn("{} message with type:{} should not be contained in stream", msg.did(), msg.pb().getType());
                    throw new IllegalArgumentException("unhandled streamed protocol type " + msg.pb().getType().name());
            }
        } catch (Exception e) {
            l.warn("{} fail process stream head cause:{}", LogUtil.suppress(e));
            _f._iss.end_(key);
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

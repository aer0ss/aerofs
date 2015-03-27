/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadCredential;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.base.ex.ExTimeout;
import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;
import com.aerofs.daemon.transport.lib.StreamKey;
import com.aerofs.daemon.core.protocol.CoreProtocolUtil;
import com.aerofs.daemon.core.store.ExSIDNotFound;
import com.aerofs.daemon.event.net.Endpoint;
import com.aerofs.daemon.lib.id.StreamID;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExDeviceOffline;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.proto.Core.PBCore;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.io.InputStream;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;

public class CoreProtocolReactor implements IUnicastInputLayer
{
    private static final Logger l = Loggers.getLogger(CoreProtocolReactor.class);

    public interface Handler
    {
        PBCore.Type message();
        void handle_(DigestedMessage msg) throws Exception;
    }

    private final DeviceToUserMapper _d2u;
    private final IncomingStreams _iss;

    private final ImmutableMap<PBCore.Type, Handler> _handlers;

    @Inject
    private CoreProtocolReactor(
            IncomingStreams iss,
            Set<Handler> handlers,
            DeviceToUserMapper d2u)
    {
        _iss = iss;
        _d2u = d2u;
        ImmutableMap.Builder<PBCore.Type, Handler> bd = ImmutableMap.builder();
        handlers.stream().forEach(h -> bd.put(h.message(), h));
        _handlers = bd.build();
    }

    @Override
    public void onUnicastDatagramReceived_(RawMessage r, PeerContext pc)
    {
        DID did = pc.ep().did();

        try {
            checkState(!did.equals(Cfg.did()));
            _d2u.onUserIDResolved_(did, pc.user());
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
            UserID userID = _d2u.getUserIDForDIDNullable_(did);
            if (userID == null) {
                // FIXME (AG): This should be a queue of DID -> pending multicast packets
                // This would ensure that only the first thread waits
                // Other threads would place their multicast packet into the queue
                // and when the UserID is resolved the original thread can
                // process all pending messages
                userID = _d2u.issuePeerToPeerResolveUserIDRequest_(did);
            }
            PBCore pb = PBCore.parseDelimitedFrom(is);
            processIncomingMessage_(new DigestedMessage(pb, is, ep, userID, null), true);
        } catch (Exception e) {
            l.warn("{} fail process mc", did, LogUtil.suppress(e,
                    ExTimeout.class, ExDeviceOffline.class, ExBadCredential.class,
                    ExSIDNotFound.class, ExNoResource.class));
            SystemUtil.fatalOnUncheckedException(e);
        }
    }

    private void processIncomingMessage_(DigestedMessage msg, boolean isMulticast)
            throws Exception {
        l.debug("{} <- {} {},{} over {}", msg.did(), isMulticast ? "mc" : "uc",
                CoreProtocolUtil.typeString(msg.pb()), msg.pb().getRpcid(), msg.tp());

        handle_(msg);
    }

    private void handle_(DigestedMessage msg) throws Exception
    {
        switch (msg.pb().getType()) {
        case RESOLVE_USER_ID_REQUEST:
            _d2u.respondToPeerToPeerResolveUserIDRequest_(msg);
            break;
        case RESOLVE_USER_ID_RESPONSE:
            // noop - this incoming message results in _d2u being updated in onUnicastDatagramReceived_
            break;
        default:
            Handler h = _handlers.get(msg.pb().getType());
            if (h != null) {
                h.handle_(msg);
            } else {
                l.warn("{} unknown message type {}", msg.did(), msg.pb().getType());
                throw new ExProtocolError("unhandled msg type " + msg.pb().getType().name());
            }
        }
    }

    @Override
    public void onStreamBegun_(StreamID streamId, RawMessage r, PeerContext pc)
    {
        StreamKey key = new StreamKey(pc.ep().did(), streamId);
        try {
            _iss.begun_(key, pc);

            PBCore pb = PBCore.parseDelimitedFrom(r._is);
            DigestedMessage msg = new DigestedMessage(pb, r._is, pc.ep(), pc.user(), key);

            l.debug("{} <- uc {},{} over {} via {}", msg.did(),
                    CoreProtocolUtil.typeString(msg.pb()), msg.pb().getRpcid(), msg.tp(), streamId);

            handle_(msg);
        } catch (Exception e) {
            l.warn("{} fail process stream head cause:{}", pc.ep().did(), LogUtil.suppress(e));
            _iss.end_(key);
        }
    }
}

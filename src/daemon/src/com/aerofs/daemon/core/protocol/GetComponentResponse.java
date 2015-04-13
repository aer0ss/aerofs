/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.protocol;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.base.ex.Exceptions;
import com.aerofs.daemon.core.Hasher;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.alias.MapAlias2Target;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.net.IncomingStreams;
import com.aerofs.daemon.core.protocol.ContentUpdater.ReceivedContent;
import com.aerofs.daemon.core.transfers.download.IDownloadContext;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.lib.id.*;
import com.aerofs.proto.Core.PBGetComponentResponse;
import com.aerofs.proto.Core.PBMeta;
import com.google.inject.Inject;
import org.slf4j.Logger;

public class GetComponentResponse
{
    private static final Logger l = Loggers.getLogger(GetComponentResponse.class);

    private final IncomingStreams _iss;
    private final MetaUpdater _mu;
    private final LocalACL _lacl;
    private final MapAlias2Target _a2t;
    private final LegacyCausality _legacyCausality;
    private final Hasher _hasher;
    private final ComputeHash _computeHash;
    private final ContentUpdater _cu;

    @Inject
    public GetComponentResponse(MetaUpdater mu, ContentUpdater cu, IncomingStreams iss,
                                LocalACL lacl, MapAlias2Target a2t, LegacyCausality legacy,
                                Hasher hasher, ComputeHash computeHash)
    {
        _mu = mu;
        _cu = cu;
        _iss = iss;
        _a2t = a2t;
        _lacl = lacl;
        _hasher = hasher;
        _computeHash = computeHash;
        _legacyCausality = legacy;
    }

    public static OA.Type fromPB(PBMeta.Type type)
    {
        return OA.Type.valueOf(type.ordinal());
    }

    /**
     * @param msg the message sent in response to GetComponentRequest
     */
    public void processResponse_(SOCID socid, DigestedMessage msg, IDownloadContext cxt)
            throws Exception
    {
        try {
            if (msg.pb().hasExceptionResponse()) {
                throw BaseLogUtil.suppress(Exceptions.fromPB(msg.pb().getExceptionResponse()));
            }
            Util.checkPB(msg.pb().hasGetComponentResponse(), PBGetComponentResponse.class);
            processResponseInternal_(socid, msg, cxt);
        } finally {
            // TODO put this statement into a more general method
            if (msg.streamKey() != null) _iss.end_(msg.streamKey());
        }
    }

    // on ExDependsOn, the download is suspended, and _dls.scheduleAndEnqueue_()
    // will be called upon resolution of the dependency.
    //
    // TODO on throwing DependsOn, save whatever downloaded in dl and avoid
    // downloading again after the dependency is solved.
    //

    private void processResponseInternal_(SOCID socid, DigestedMessage msg, IDownloadContext cxt)
            throws Exception
    {
        /////////////////////////////////////////
        // determine meta diff, handle aliases, check permissions, etc
        //
        // N.B. ExSenderHasNoPerm rather than ExNoPerm should be thrown if the sender has no
        // permission to update the component, so that the caller of this method can differentiate
        // this case from the case when the sender replies a ExNoPerm to us, indicating that we as
        // the receiver has no permission to read. Therefore, we can't use _dacl.checkThrows().

        // TODO check for parent permissions for creation / moving?

        // if permission checking failed, the GetComponentResponse is malicious or the
        // file permission was reduced between GetComponentRequest and GetComponentResponse.
        // TODO notify user about this, and remove from KML the version
        // specified in the response message?
        if (socid.cid().isMeta()) {
            _mu.processMetaResponse_(socid, msg, cxt);
        } else if (socid.cid().isContent()) {
            processContentResponse_(socid.soid(), msg, cxt);
        } else {
            l.error("{} unsupported cid: {}", msg.did(), socid);
            throw new ExProtocolError("unsupported CID: " + socid.cid());
        }
    }

    private void processContentResponse_(SOID soid, DigestedMessage msg, IDownloadContext cxt)
            throws Exception {
        // see Rule 2 in acl.md
        if (!_lacl.check_(msg.user(), soid.sidx(), Permissions.EDITOR)) {
            l.warn("{} on {} has no editor perm for {}", msg.user(), msg.ep(), soid.sidx());
            throw new ExSenderHasNoPerm();
        }

        // We should abort when receiving content for an aliased object
        if (_a2t.isAliased_(soid)) {
            throw new ExAborted(soid + " aliased");
        }

        PBGetComponentResponse response = msg.pb().getGetComponentResponse();
        ReceivedContent content = new ReceivedContent(response.getMtime(), response.getFileTotalLength(),
                response.getPrefixLength(), Version.fromPB(response.getVersion()),
                response.hasHash() ? new ContentHash(response.getHash()) : null);
        CausalityResult cr;
        try {
            if (response.hasIsContentSame() && response.getIsContentSame()) {
                cr = _legacyCausality.contentSame(soid, content);
            } else {
                cr = _legacyCausality.computeCausality_(soid, content);
            }
        } catch (ExRestartWithHashComputed e) {
            l.debug("Fetching hash on demand");
            // Abort the ongoing transfer.  If we don't, the peer will continue sending
            // file content which we will queue until we exhaust the heap.
            if (msg.streamKey() != null) {
                _iss.end_(msg.streamKey());
            }
            // Compute the local ContentHash first.  This ensures that the local ContentHash
            // is available by the next time we try to download this component.
            _hasher.computeHash_(new SOKID(soid, e.kidx), false, cxt.token());
            // Send a ComputeHashCall to make the remote peer also compute the ContentHash.
            // This will block for a while until the remote peer computes the hash.
            _computeHash.issueRequest_(soid, content.vRemote, msg.did(), cxt.token());
            // Once the above call returns, throw so Downloads will restart this Download.
            // The next time through, the peer should send the hash. Since we already
            // computed the local hash, we can compare them.
            throw e;
        }

        if (cr == null) return;

        _cu.processContentResponse_(soid, content, msg, cr,cxt.token());
    }
}

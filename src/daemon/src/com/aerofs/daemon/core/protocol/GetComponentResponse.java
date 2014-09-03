/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.protocol;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExProtocolError;
import com.aerofs.base.ex.Exceptions;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.net.IncomingStreams;
import com.aerofs.daemon.core.transfers.download.IDownloadContext;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.SOCID;
import com.aerofs.proto.Core.PBGetComponentResponse;
import com.aerofs.proto.Core.PBMeta;
import com.google.inject.Inject;
import org.slf4j.Logger;

public class GetComponentResponse
{
    private static final Logger l = Loggers.getLogger(GetComponentResponse.class);

    private final IncomingStreams _iss;
    private final MetaUpdater _mu;
    private final ContentUpdater _cu;

    @Inject
    public GetComponentResponse(MetaUpdater mu, ContentUpdater cu, IncomingStreams iss)
    {
        _mu = mu;
        _cu = cu;
        _iss = iss;
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
            _cu.processContentResponse_(socid, msg, cxt);
        } else {
            l.error("{} unsupported cid: {}", msg.did(), socid);
            throw new ExProtocolError("unsupported CID: " + socid.cid());
        }
    }
}

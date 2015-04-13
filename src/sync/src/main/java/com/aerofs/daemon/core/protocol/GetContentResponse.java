package com.aerofs.daemon.core.protocol;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.Loggers;
import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.Exceptions;
import com.aerofs.daemon.core.acl.LocalACL;
import com.aerofs.daemon.core.net.DigestedMessage;
import com.aerofs.daemon.core.net.IncomingStreams;
import com.aerofs.daemon.core.protocol.ContentUpdater.ReceivedContent;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.lib.ContentHash;
import com.aerofs.lib.Util;
import com.aerofs.lib.Version;
import com.aerofs.lib.id.SOID;
import com.aerofs.proto.Core.PBGetContentResponse;
import com.google.inject.Inject;
import org.slf4j.Logger;

public class GetContentResponse
{
    private static final Logger l = Loggers.getLogger(GetContentResponse.class);

    private final LocalACL _lacl;
    private final ContentUpdater _cu;
    private final IncomingStreams _iss;
    private final Causality _causality;

    @Inject
    public GetContentResponse(LocalACL lacl, ContentUpdater cu, IncomingStreams iss,
                              Causality causality)
    {
        _lacl = lacl;
        _cu = cu;
        _iss = iss;
        _causality = causality;
    }

    public void processResponse_(SOID soid, DigestedMessage msg, Token tk)
            throws Exception
    {
        try {
            if (msg.pb().hasExceptionResponse()) {
                throw BaseLogUtil.suppress(Exceptions.fromPB(msg.pb().getExceptionResponse()));
            }
            Util.checkPB(msg.pb().hasGetContentResponse(), PBGetContentResponse.class);
            processContentResponse_(soid, msg, tk);
        } finally {
            // TODO put this statement into a more general method
            if (msg.streamKey() != null) _iss.end_(msg.streamKey());
        }
    }

    private void processContentResponse_(SOID soid, DigestedMessage msg, Token tk)
            throws Exception {
        // see Rule 2 in acl.md
        if (!_lacl.check_(msg.user(), soid.sidx(), Permissions.EDITOR)) {
            l.warn("{} on {} has no editor perm for {}", msg.user(), msg.ep(), soid.sidx());
            throw new ExSenderHasNoPerm();
        }

        PBGetContentResponse response = msg.pb().getGetContentResponse();
        ReceivedContent content = new ReceivedContent(response.getMtime(), response.getLength(),
                response.getPrefixLength(), Version.wrapCentral(response.getVersion()),
                new ContentHash(response.getHash()));
        CausalityResult cr = _causality.computeCausality_(soid, content);

        if (cr == null) return;

        _cu.processContentResponse_(soid, content, msg, cr, tk);
    }
}

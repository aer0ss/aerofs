package com.aerofs.daemon.rest.handler;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.daemon.core.activity.OutboundEventLogger;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.rest.event.EIListChildren;
import com.aerofs.daemon.rest.util.MimeTypeDetector;
import com.aerofs.oauth.Scope;
import com.aerofs.rest.api.ChildrenList;
import com.google.inject.Inject;

import java.sql.SQLException;

import static com.aerofs.daemon.core.activity.OutboundEventLogger.META_REQUEST;

public class HdListChildren extends AbstractRestHdIMC<EIListChildren>
{
    @Inject private IMapSIndex2SID _sidx2sid;
    @Inject private MimeTypeDetector _detector;
    @Inject private OutboundEventLogger _eol;

    @Override
    protected void handleThrows_(EIListChildren ev) throws ExNotFound, SQLException
    {
        OA oa = _access.resolveFollowsAnchor_(ev._object, ev._token);

        requireAccessToFile(ev._token, Scope.READ_FILES, oa);

        ChildrenList l = _mb.children(ev._object.toStringFormal(), oa, ev._includeParent);

        // NB: technically we're sending meta about all the children, should we log them too?
        _eol.log_(META_REQUEST, oa.soid(), ev.did());

        ev.setResult_(l);
    }
}

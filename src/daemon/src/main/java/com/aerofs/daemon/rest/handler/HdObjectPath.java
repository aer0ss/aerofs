/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.rest.handler;

import com.aerofs.daemon.core.activity.OutboundEventLogger;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.rest.event.EIObjectPath;
import com.aerofs.oauth.Scope;
import com.google.inject.Inject;


import static com.aerofs.daemon.core.activity.OutboundEventLogger.META_REQUEST;

public class HdObjectPath extends AbstractRestHdIMC<EIObjectPath>
{
    @Inject private OutboundEventLogger _eol;

    @Override
    protected void restHandleThrows_(EIObjectPath ev) throws Exception
    {
        OA oa = _access.resolve_(ev._object, ev._token);
        ResolvedPath p = requireAccessToFile(ev._token, Scope.READ_FILES, oa);

        // NB: technically we're sending meta about all the children, should we log them too?
        _eol.log_(META_REQUEST, oa.soid(), ev.did());

        ev.setResult_(_mb.path(p, ev._token));
    }
}

/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.rest.handler;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.object.ObjectDeleter;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.daemon.rest.event.EIDeleteObject;
import com.aerofs.daemon.rest.util.EntityTagUtil;
import com.aerofs.daemon.rest.util.MetadataBuilder;
import com.aerofs.daemon.rest.util.RestObjectResolver;
import com.aerofs.rest.api.Error;
import com.aerofs.rest.api.Error.Type;
import com.google.inject.Inject;

import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

public class HdDeleteObject extends AbstractRestHdIMC<EIDeleteObject>
{
    private final ObjectDeleter _od;

    @Inject
    public HdDeleteObject(RestObjectResolver access, MetadataBuilder mb, TransManager tm,
            ObjectDeleter od, EntityTagUtil etags)
    {
        super(access, etags, mb, tm);
        _od = od;
    }

    @Override
    protected void handleThrows_(EIDeleteObject ev) throws Exception
    {
        if (!ev._token.isAllowedToWrite()) throw new ExNoPerm();
        OA from = _access.resolveWithPermissions_(ev._object, ev.user(), Permissions.EDITOR);

        EntityTag etag = _etags.etagForObject(from.soid());

        if (ev._ifMatch.isValid() && !ev._ifMatch.matches(etag)) {
            ev.setResult_(Response.status(Status.PRECONDITION_FAILED)
                    .entity(new Error(Type.CONFLICT,
                            "Delete operation failed due to a concurrent update.")));
            return;
        }

        Trans t = _tm.begin_();
        try {
            _od.delete_(from.soid(), PhysicalOp.APPLY, t);
            t.commit_();
        } finally {
            t.end_();
        }

        ev.setResult_(Response.noContent());
    }
}

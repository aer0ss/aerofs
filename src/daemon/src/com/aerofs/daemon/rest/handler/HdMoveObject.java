/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.rest.handler;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.migration.ImmigrantCreator;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.daemon.rest.event.EIMoveObject;
import com.aerofs.daemon.rest.util.EntityTagUtil;
import com.aerofs.daemon.rest.util.MetadataBuilder;
import com.aerofs.daemon.rest.util.RestObjectResolver;
import com.aerofs.lib.id.SOID;
import com.aerofs.rest.api.Error;
import com.aerofs.rest.api.Error.Type;
import com.google.inject.Inject;

import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

public class HdMoveObject extends AbstractRestHdIMC<EIMoveObject>
{
    private final ImmigrantCreator _imc;

    @Inject
    public HdMoveObject(RestObjectResolver access, EntityTagUtil etags, ImmigrantCreator imc,
            MetadataBuilder mb, TransManager tm)
    {
        super(access, etags, mb, tm);
        _imc = imc;
    }

    @Override
    protected void handleThrows_(EIMoveObject ev) throws Exception
    {
        if (!ev._token.isAllowedToWrite()) throw new ExNoPerm();
        OA from = _access.resolveWithPermissions_(ev._object, ev.user(), Permissions.EDITOR);
        OA toParent = _access.resolveWithPermissions_(ev._newParent, ev.user(), Permissions.EDITOR);

        EntityTag etag = _etags.etagForObject(from.soid());

        if (ev._ifMatch.isValid() && !ev._ifMatch.matches(etag)) {
            ev.setResult_(Response.status(Status.PRECONDITION_FAILED)
                    .entity(new Error(Type.CONFLICT,
                            "Rename operation failed due to a concurrent update.")));
            return;
        }

        Trans t = _tm.begin_();
        SOID soid;
        try {
            soid = _imc.move_(from.soid(), toParent.soid(), ev._newName, PhysicalOp.APPLY, t);
            t.commit_();
        } catch (ExAlreadyExist e) {
            ev.setResult_(Response
                    .status(Status.CONFLICT)
                    .entity(new Error(Error.Type.CONFLICT,
                            "Object with this name already exists at this location")));
            return;
        } finally {
            t.end_();
        }

        ev.setResult_(Response.ok()
                .entity(_mb.metadata(soid))
                .tag(_etags.etagForObject(soid)));
    }
}

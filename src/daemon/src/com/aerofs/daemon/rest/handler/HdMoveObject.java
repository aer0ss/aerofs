/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.rest.handler;

import com.aerofs.base.acl.Role;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.migration.ImmigrantCreator;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.daemon.rest.event.EIMoveObject;
import com.aerofs.daemon.rest.util.AccessChecker;
import com.aerofs.daemon.rest.util.EntityTagUtil;
import com.aerofs.daemon.rest.util.MetadataBuilder;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.id.SOID;
import com.aerofs.rest.api.Error;
import com.aerofs.rest.api.Error.Type;
import com.google.inject.Inject;

import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

public class HdMoveObject extends AbstractHdIMC<EIMoveObject>
{
    private final TransManager _tm;
    private final AccessChecker _access;
    private final ImmigrantCreator _imc;
    private final MetadataBuilder _mb;
    private final EntityTagUtil _etags;

    @Inject
    public HdMoveObject(AccessChecker access, EntityTagUtil etags, ImmigrantCreator imc,
            MetadataBuilder mb, TransManager tm)
    {
        _tm = tm;
        _access = access;
        _imc = imc;
        _mb = mb;
        _etags = etags;
    }

    @Override
    protected void handleThrows_(EIMoveObject ev, Prio prio) throws Exception
    {
        OA from = _access.checkObject_(ev._object, ev._user, Role.EDITOR);
        OA toParent = _access.checkObject_(ev._newParent, ev._user, Role.EDITOR);

        EntityTag etag = _etags.etagForObject(from.soid());

        if (ev._ifMatch != null && !ev._ifMatch.matches(etag)) {
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
        } finally {
            t.end_();
        }

        ev.setResult_(Response.ok()
                .entity(_mb.metadata(soid))
                .tag(_etags.etagForObject(soid)));
    }
}

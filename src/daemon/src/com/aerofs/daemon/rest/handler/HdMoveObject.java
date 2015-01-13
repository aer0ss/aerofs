/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.rest.handler;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.migration.ImmigrantCreator;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.rest.event.EIMoveObject;
import com.aerofs.lib.id.SOID;
import com.aerofs.oauth.Scope;
import com.aerofs.rest.api.Error;
import com.aerofs.rest.api.Error.Type;
import com.google.inject.Inject;

import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import static com.google.common.base.Preconditions.checkArgument;

public class HdMoveObject extends AbstractRestHdIMC<EIMoveObject>
{
    @Inject private ImmigrantCreator _imc;

    @Override
    protected void handleThrows_(EIMoveObject ev) throws Exception
    {
        OA from = _access.resolveWithPermissions_(ev._object, ev._token, Permissions.EDITOR);
        OA toParent = _access.resolveFollowsAnchorWithPermissions_(ev._newParent, ev._token,
                Permissions.EDITOR);

        requireAccessToFile(ev._token, Scope.WRITE_FILES, from);
        requireAccessToFile(ev._token, Scope.WRITE_FILES, toParent);

        checkArgument(!from.soid().oid().isRoot() && !from.soid().oid().isTrash(),
                "cannot move system folder");
        checkArgument(toParent.isDir(), "parent field must point to a valid folder");

        EntityTag etag = _etags.etagForMeta(from.soid());

        if (ev._ifMatch.isValid() && !ev._ifMatch.matches(etag)) {
            ev.setResult_(Response.status(Status.PRECONDITION_FAILED)
                    .entity(new Error(Type.CONFLICT,
                            "Rename operation failed due to a concurrent update.")));
            return;
        }

        SOID soid;
        try (Trans t = _tm.begin_()) {
            soid = _imc.move_(from.soid(), toParent.soid(), ev._newName, PhysicalOp.APPLY, t);
            t.commit_();
        } catch (ExAlreadyExist e) {
            ev.setResult_(Response
                    .status(Status.CONFLICT)
                    .entity(new Error(Error.Type.CONFLICT,
                            "Object with this name already exists at this location")));
            return;
        }

        ev.setResult_(Response.ok()
                .entity(_mb.metadata(soid, ev._token))
                .tag(_etags.etagForMeta(soid)));
    }
}

package com.aerofs.daemon.rest.handler;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.base.id.RestObject;
import com.aerofs.daemon.rest.event.EICreateObject;
import com.aerofs.lib.id.SOID;
import com.aerofs.oauth.Scope;
import com.aerofs.rest.api.Error;
import com.aerofs.restless.Service;
import com.google.inject.Inject;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.net.URI;

import static com.google.common.base.Preconditions.checkArgument;

public class HdCreateObject extends AbstractRestHdIMC<EICreateObject>
{
    @Inject private ObjectCreator _oc;

    @Override
    protected void handleThrows_(EICreateObject ev) throws Exception
    {
        OA oaParent = _access.resolveFollowsAnchorWithPermissions_(ev._parent, ev._token,
                Permissions.EDITOR);

        requireAccessToFile(ev._token, Scope.WRITE_FILES, oaParent);

        checkArgument(oaParent.isDir(), "parent field must point to a valid folder");

        Trans t = _tm.begin_();
        SOID soid;
        try {
            soid = _oc.create_(ev._folder ? Type.DIR : Type.FILE, oaParent.soid(), ev._name,
                    PhysicalOp.APPLY, t);
            t.commit_();
        } catch (ExAlreadyExist e) {
            ev.setResult_(Response
                    .status(Status.CONFLICT)
                    .entity(new Error(Error.Type.CONFLICT,
                            "A file already exists at this location")));
            return;
        } finally {
            t.end_();
        }

        RestObject object = _mb.object(soid);

        String location = Service.DUMMY_LOCATION
                + 'v' + ev._version
                + '/' + (ev._folder ? "folders" : "files")
                + '/' + object.toStringFormal();

        ev.setResult_(Response.created(URI.create(location))
                .entity(_mb.metadata(soid, ev._token))
                .tag(_etags.etagForMeta(soid)));
    }
}

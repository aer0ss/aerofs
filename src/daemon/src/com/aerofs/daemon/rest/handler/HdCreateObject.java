package com.aerofs.daemon.rest.handler;

import com.aerofs.base.acl.Permissions;
import com.aerofs.base.ex.ExAlreadyExist;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.OA.Type;
import com.aerofs.daemon.core.object.ObjectCreator;
import com.aerofs.daemon.core.phy.PhysicalOp;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.daemon.rest.util.RestObject;
import com.aerofs.daemon.rest.event.EICreateObject;
import com.aerofs.daemon.rest.util.EntityTagUtil;
import com.aerofs.daemon.rest.util.MetadataBuilder;
import com.aerofs.daemon.rest.util.RestObjectResolver;
import com.aerofs.lib.id.SOID;
import com.aerofs.rest.api.Error;
import com.aerofs.restless.Service;
import com.google.inject.Inject;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.net.URI;

public class HdCreateObject extends AbstractRestHdIMC<EICreateObject>
{
    private final ObjectCreator _oc;

    @Inject
    public HdCreateObject(RestObjectResolver access, ObjectCreator oc, TransManager tm,
            MetadataBuilder mb, EntityTagUtil etags)
    {
        super(access, etags, mb, tm);
        _oc = oc;
    }

    @Override
    protected void handleThrows_(EICreateObject ev) throws Exception
    {
        OA oaParent = _access.resolveWithPermissions_(ev._parent, ev.user(), Permissions.EDITOR);

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

        RestObject object = new RestObject(ev._parent.sid, soid.oid());

        String location = Service.DUMMY_LOCATION
                + 'v' + ev._version
                + '/' + (ev._folder ? "folders" : "files")
                + '/' + object.toStringFormal();

        ev.setResult_(Response.created(URI.create(location))
                .entity(_mb.metadata(soid))
                .tag(_etags.etagForObject(soid)));
    }
}

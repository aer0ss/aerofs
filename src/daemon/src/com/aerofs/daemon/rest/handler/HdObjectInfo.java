package com.aerofs.daemon.rest.handler;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.rest.event.EIObjectInfo;
import com.aerofs.daemon.rest.event.EIObjectInfo.Type;
import com.aerofs.daemon.rest.util.RestObjectResolver;
import com.aerofs.daemon.rest.util.EntityTagUtil;
import com.aerofs.daemon.rest.util.MetadataBuilder;
import com.aerofs.lib.event.Prio;
import com.google.inject.Inject;

import javax.ws.rs.core.Response;
import java.sql.SQLException;

public class HdObjectInfo extends AbstractHdIMC<EIObjectInfo>
{
    private final RestObjectResolver _access;
    private final MetadataBuilder _mb;
    private final EntityTagUtil _etags;

    @Inject
    public HdObjectInfo(RestObjectResolver access, MetadataBuilder mb, EntityTagUtil etags)
    {
        _access = access;
        _mb = mb;
        _etags = etags;
    }

    @Override
    protected void handleThrows_(EIObjectInfo ev, Prio prio) throws ExNotFound, SQLException
    {
        OA oa = _access.resolve_(ev._object, ev._user);
        if (oa.isDirOrAnchor() != (ev._type == Type.FOLDER)) throw new ExNotFound();
        ev.setResult_(Response.ok()
                .entity(_mb.metadata(oa))
                .tag(_etags.etagForObject(oa.soid())));
    }
}

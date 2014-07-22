package com.aerofs.daemon.rest.handler;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.daemon.core.activity.OutboundEventLogger;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.rest.event.EIObjectInfo;
import com.aerofs.daemon.rest.event.EIObjectInfo.Type;
import com.aerofs.oauth.Scope;
import com.google.inject.Inject;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import java.sql.SQLException;

import static com.aerofs.daemon.core.activity.OutboundEventLogger.*;

public class HdObjectInfo extends AbstractRestHdIMC<EIObjectInfo>
{
    @Inject private OutboundEventLogger _eol;

    @Override
    protected void handleThrows_(EIObjectInfo ev) throws ExNotFound, SQLException
    {
        OA oa = _access.resolve_(ev._object, ev._token);
        if (oa.isDirOrAnchor() != (ev._type == Type.FOLDER)) throw new ExNotFound();

        requireAccessToFile(ev._token, Scope.READ_FILES, oa);

        _eol.log_(META_REQUEST, oa.soid(), ev.did());

        ResponseBuilder r = Response.ok().entity(_mb.metadata(oa, ev._token, ev._fields));

        // NB: only include Etag when response does not include  on-demand fields
        ev.setResult_(ev._fields == null ? r.tag(_etags.etagForMeta(oa.soid())) : r);
    }
}

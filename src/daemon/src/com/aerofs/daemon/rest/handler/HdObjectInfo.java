package com.aerofs.daemon.rest.handler;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.daemon.core.activity.OutboundEventLogger;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.daemon.rest.event.EIObjectInfo;
import com.aerofs.daemon.rest.event.EIObjectInfo.Type;
import com.aerofs.daemon.rest.util.RestObjectResolver;
import com.aerofs.daemon.rest.util.EntityTagUtil;
import com.aerofs.daemon.rest.util.MetadataBuilder;
import com.google.inject.Inject;

import javax.ws.rs.core.Response;
import java.sql.SQLException;

import static com.aerofs.daemon.core.activity.OutboundEventLogger.*;

public class HdObjectInfo extends AbstractRestHdIMC<EIObjectInfo>
{
    private final OutboundEventLogger _eol;

    @Inject
    public HdObjectInfo(RestObjectResolver access, MetadataBuilder mb, EntityTagUtil etags,
            TransManager tm, OutboundEventLogger eol)
    {
        super(access, etags, mb, tm);
        _eol = eol;
    }

    @Override
    protected void handleThrows_(EIObjectInfo ev) throws ExNotFound, SQLException
    {
        OA oa = _access.resolve_(ev._object, ev._user);
        if (oa.isDirOrAnchor() != (ev._type == Type.FOLDER)) throw new ExNotFound();

        _eol.log_(META_REQUEST, oa.soid(), ev._did);

        ev.setResult_(Response.ok()
                .entity(_mb.metadata(oa))
                .tag(_etags.etagForObject(oa.soid())));
    }
}

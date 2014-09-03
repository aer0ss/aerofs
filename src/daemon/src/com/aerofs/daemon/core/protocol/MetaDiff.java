package com.aerofs.daemon.core.protocol;

import com.aerofs.base.id.OID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.lib.id.SOID;
import com.aerofs.proto.Core.PBMeta;
import com.google.inject.Inject;

import java.sql.SQLException;

public class MetaDiff
{
    private final DirectoryService _ds;

    @Inject
    public MetaDiff(DirectoryService ds)
    {
        _ds = ds;
    }

    public static final int PARENT      = 0x01;
    public static final int NAME        = 0x02;

    public int computeMetaDiff_(SOID soid, PBMeta meta, OID oidParent)
        throws SQLException
    {
        // N.B. as this stands, if {@code soid} is locally aliased, then we will always report a
        // metaDiff of PARENT | NAME. I'm not sure whether that is correct or not.
        OA oa = _ds.getOANullable_(soid);

        int diff = 0;
        if (oa == null) {
            diff = PARENT | NAME;
        } else {
            assert oa.type() == OA.Type.valueOf(meta.getType().ordinal());

            // TODO (MJ) is it worth asserting that oidParent is not a locally aliased object?
            if (!oa.parent().equals(oidParent)) diff |= PARENT;
            if (!oa.name().equals(meta.getName())) diff |= NAME;
        }

        return diff;
    }
}

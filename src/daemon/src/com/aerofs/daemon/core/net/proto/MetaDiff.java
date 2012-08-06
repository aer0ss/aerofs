package com.aerofs.daemon.core.net.proto;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.OID;
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
    public static final int FLAGS       = 0x20;

    public int computeMetaDiff_(SOID soid, PBMeta meta, OID oidParent)
        throws SQLException
    {
        OA oa = _ds.getOANullable_(soid);

        assert !Util.test(meta.getFlags(), OA.FLAGS_LOCAL);

        int diff = 0;
        if (oa == null) {
            diff = PARENT | NAME;
            if (meta.getFlags() != 0) diff |= FLAGS;

        } else {
            assert oa.type() == OA.Type.valueOf(meta.getType().ordinal());

            if (!oa.parent().equals(oidParent)) diff |= PARENT;
            if (!oa.name().equals(meta.getName())) diff |= NAME;
            if ((oa.flags() & ~OA.FLAGS_LOCAL) != meta.getFlags()) diff |= FLAGS;
        }

        return diff;
    }
}

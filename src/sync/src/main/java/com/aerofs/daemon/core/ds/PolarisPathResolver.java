package com.aerofs.daemon.core.ds;

import com.aerofs.daemon.core.polaris.db.RemoteLinkDatabase;
import com.aerofs.daemon.core.polaris.db.RemoteLinkDatabase.RemoteLink;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.ids.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.Lists;

import javax.inject.Inject;
import java.sql.SQLException;
import java.util.List;

public class PolarisPathResolver implements IPathResolver {
    private final RemoteLinkDatabase _rldb;
    private final IMapSIndex2SID _sidx2sid;

    @Inject
    public PolarisPathResolver(RemoteLinkDatabase rldb, IMapSIndex2SID sidx2sid)
    {
        _rldb = rldb;
        _sidx2sid = sidx2sid;
    }

    @Override
    public ResolvedPath resolveNullable_(SOID soid) throws SQLException {
        List<SOID> soids = Lists.newArrayListWithCapacity(16);
        List<String> elems = Lists.newArrayListWithCapacity(16);
        OID oid = soid.oid();
        SIndex sidx = soid.sidx();
        while (!oid.isRoot()) {
            RemoteLink rl = _rldb.getParent_(sidx, oid);
            if (rl == null) return null;
            soids.add(new SOID(sidx, oid));
            elems.add(rl.name);
            oid = rl.parent;
        }
        return new ResolvedPath(_sidx2sid.get_(sidx), Lists.reverse(soids), Lists.reverse(elems));
    }
}

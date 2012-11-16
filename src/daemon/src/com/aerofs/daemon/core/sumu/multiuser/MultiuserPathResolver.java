/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.sumu.multiuser;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.IPathResolver;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.lib.Path;
import com.aerofs.lib.ex.ExFormatError;
import com.aerofs.lib.id.OID;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.Lists;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.List;

public class MultiuserPathResolver implements IPathResolver
{

    private final DirectoryService _ds;
    private final IMapSIndex2SID _sidx2sid;
    private final IMapSID2SIndex _sid2sidx;

    public MultiuserPathResolver(DirectoryService ds, IMapSIndex2SID sidx2sid,
            IMapSID2SIndex sid2sidx)
    {
        _ds = ds;
        _sidx2sid = sidx2sid;
        _sid2sidx = sid2sidx;
    }

    @Override
    public @Nonnull List<String> resolve_(@Nonnull OA oa) throws SQLException
    {
        List<String> elems = Lists.newArrayListWithCapacity(16);

        while (!oa.soid().oid().isRoot()) {
            elems.add(oa.name());
            oa = _ds.getOA_(new SOID(oa.soid().sidx(), oa.parent()));
        }

        elems.add(_sidx2sid.get_(oa.soid().sidx()).toStringFormal());

        return elems;
    }

    @Override
    public @Nullable SOID resolve_(@Nonnull Path path) throws SQLException
    {
        // Paths in multi-user systems must not be empty
        if (path.isEmpty()) return null;

        // Decode the first path element to SID
        SID sid;
        try {
            sid = new SID(path.elements()[0]);
        } catch (ExFormatError e) {
            // TODO (WW) use UniqueID.fromStringNullable() to avoid exception throwing
            return null;
        }

        SIndex sidx = _sid2sidx.getNullable_(sid);
        if (sidx == null) return null;

        OID oid = OID.ROOT;
        for (int i = 1; i < path.elements().length; i++) {
            oid = _ds.getChild_(sidx, oid, path.elements()[i]);
            if (oid == null) return null;
        }

        return new SOID(sidx, oid);
    }
}

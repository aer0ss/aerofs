/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.multiuser;

import com.aerofs.daemon.core.ds.AbstractPathResolver;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.Lists;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.sql.SQLException;
import java.util.List;

public class MultiuserPathResolver extends AbstractPathResolver
{
    public static class Factory implements AbstractPathResolver.Factory
    {
        private final IMapSIndex2SID _sidx2sid;
        private final IMapSID2SIndex _sid2sidx;

        @Inject
        public Factory(IMapSIndex2SID sidx2sid, IMapSID2SIndex sid2sidx)
        {
            _sid2sidx = sid2sidx;
            _sidx2sid = sidx2sid;
        }

        @Override
        public AbstractPathResolver create(DirectoryService ds)
        {
            return new MultiuserPathResolver(ds, this);
        }
    }

    private MultiuserPathResolver(DirectoryService ds, Factory f)
    {
        super(ds, f._sidx2sid, f._sid2sidx);
    }

    @Override
    public @Nonnull ResolvedPath resolve_(@Nonnull OA oa) throws SQLException
    {
        List<SOID> soids = Lists.newArrayListWithCapacity(16);
        List<String> elems = Lists.newArrayListWithCapacity(16);

        while (!oa.soid().oid().isRoot()) {
            soids.add(oa.soid());
            elems.add(oa.name());
            oa = _ds.getOA_(new SOID(oa.soid().sidx(), oa.parent()));
        }

        return makePath_(oa.soid().sidx(), soids, elems);
    }
}

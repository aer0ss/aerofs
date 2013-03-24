/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.multiuser;

import com.aerofs.daemon.core.ds.AbstractPathResolver;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.Lists;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.sql.SQLException;
import java.util.List;

public class MultiuserPathResolver extends AbstractPathResolver
{
    @Inject
    public MultiuserPathResolver(DirectoryService ds, IMapSIndex2SID sidx2sid,
            IMapSID2SIndex sid2sidx)
    {
        super(ds, sidx2sid, sid2sidx);
    }

    @Override
    public @Nonnull Path resolve_(@Nonnull OA oa) throws SQLException
    {
        List<String> elems = Lists.newArrayListWithCapacity(16);

        while (!oa.soid().oid().isRoot()) {
            elems.add(oa.name());
            oa = _ds.getOA_(new SOID(oa.soid().sidx(), oa.parent()));
        }

        return makePath_(oa.soid().sidx(), elems);
    }
}

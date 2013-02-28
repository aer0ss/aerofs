/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.singleuser;

import com.aerofs.daemon.core.ds.AbstractPathResolver;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.lib.Path;
import com.aerofs.base.id.OID;
import com.aerofs.base.id.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.Lists;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.sql.SQLException;
import java.util.List;

public class SingleuserPathResolver extends AbstractPathResolver
{
    private final SingleuserStores _sss;
    private final DirectoryService _ds;
    private final IMapSIndex2SID _sidx2sid;

    @Inject
    public SingleuserPathResolver(SingleuserStores sss, DirectoryService ds,
            IMapSIndex2SID sidx2sid)
    {
        _sss = sss;
        _ds = ds;
        _sidx2sid = sidx2sid;
    }

    @Override
    public @Nonnull List<String> resolve_(@Nonnull OA oa) throws SQLException
    {
        List<String> elems = Lists.newArrayListWithCapacity(16);

        while (true) {
            if (oa.soid().oid().isRoot()) {
                if (_sss.isRoot_(oa.soid().sidx())) {
                    return elems;
                } else {
                    // parent oid of the root encodes the parent store's sid
                    SOID soidAnchor = getAnchor_(oa.soid().sidx());
                    assert !soidAnchor.equals(oa.soid()) : soidAnchor + " " + oa;
                    oa = _ds.getOA_(soidAnchor);
                }
            }

            elems.add(oa.name());
            assert !oa.parent().equals(oa.soid().oid()) : oa;
            oa = _ds.getOA_(new SOID(oa.soid().sidx(), oa.parent()));
        }
    }

    @Override
    public @Nullable SOID resolve_(@Nonnull Path path) throws SQLException
    {
        return resolvePath_(_ds, _sss.getRoot_(), path, 0);
    }

    /**
     * @return the SOID of the given store's anchor
     * @pre {@code sidx} must not refer to the root store
     */
    private SOID getAnchor_(SIndex sidx) throws SQLException
    {
        assert !_sss.isRoot_(sidx);
        SIndex sidxAnchor = _sss.getParent_(sidx);
        OID oidAnchor = SID.storeSID2anchorOID(_sidx2sid.get_(sidx));
        return new SOID(sidxAnchor, oidAnchor);
    }

    /**
     * @return the path to the root folder of the root store
     */
    public static Path getRootStorePath()
    {
        return new Path();
    }
}

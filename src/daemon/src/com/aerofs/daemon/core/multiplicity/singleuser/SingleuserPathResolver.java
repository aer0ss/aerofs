/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.multiplicity.singleuser;

import com.aerofs.daemon.core.ds.AbstractPathResolver;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.ds.ResolvedPath;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.ids.OID;
import com.aerofs.ids.SID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.Lists;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.sql.SQLException;
import java.util.List;

public class SingleuserPathResolver extends AbstractPathResolver
{
    private final SingleuserStoreHierarchy _sss;

    public static class Factory implements AbstractPathResolver.Factory
    {
        private final SingleuserStoreHierarchy _sh;
        private final IMapSIndex2SID _sidx2sid;
        private final IMapSID2SIndex _sid2sidx;

        @Inject
        public Factory(SingleuserStoreHierarchy sh, IMapSIndex2SID sidx2sid, IMapSID2SIndex sid2sidx)
        {
            _sh = sh;
            _sid2sidx = sid2sidx;
            _sidx2sid = sidx2sid;
        }

        @Override
        public AbstractPathResolver create(DirectoryService ds)
        {
            return new SingleuserPathResolver(ds, this);
        }
    }

    private SingleuserPathResolver(DirectoryService ds, Factory f)
    {
        super(ds, f._sidx2sid, f._sid2sidx);
        _sss = f._sh;
    }

    @Override
    public @Nonnull ResolvedPath resolve_(@Nonnull OA oa) throws SQLException
    {
        List<SOID> soids = Lists.newArrayListWithCapacity(16);
        List<String> elems = Lists.newArrayListWithCapacity(16);

        while (true) {
            if (oa.soid().oid().isRoot()) {
                if (_sss.isRoot_(oa.soid().sidx())) {
                    break;
                } else {
                    // parent oid of the root encodes the parent store's sid
                    SOID soidAnchor = getAnchor_(oa.soid().sidx());
                    assert !soidAnchor.equals(oa.soid()) : soidAnchor + " " + oa;
                    oa = _ds.getOA_(soidAnchor);
                }
            }

            soids.add(oa.soid());
            elems.add(oa.name());
            assert !oa.parent().equals(oa.soid().oid()) : oa;
            oa = _ds.getOA_(new SOID(oa.soid().sidx(), oa.parent()));
        }

        return makePath_(oa.soid().sidx(), soids, elems);
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
}

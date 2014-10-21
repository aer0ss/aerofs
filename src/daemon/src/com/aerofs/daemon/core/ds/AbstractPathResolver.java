/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.ds;

import com.aerofs.base.id.OID;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.Lists;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.List;

/**
 * This abstract class defines path hierarchies, which is how paths correspond to objects.
 *
 * In single-user systems:
 *  o There is a single root store, the path to its root folder is the empty path.
 *  o The path to the root folder of a non-root store S is the path to the root folder of its parent
 *    store T, concatenated with the path of S's anchor related to T's root folder.
 *
 * In multi-user systems:
 *  o The path to a store's root folder is "/SID" where SID is the string format of the store's SID.
 *  o The above is true regardless of whether the store is a root store.
 *
 * Also see IStores for store hierarchy, which is related to but different from path hierarchy.
 *
 * N.B. implementations should be stateless and should not cache results. The classes they
 * depend on (DirectoryService and other classes) may cache state internally. A stateful
 * implementation would cause inconsistency unless cache coherence is explicitly maintained.
 */
public abstract class AbstractPathResolver
{
    protected final DirectoryService _ds;
    protected final IMapSIndex2SID _sidx2sid;
    protected final IMapSID2SIndex _sid2sidx;

    public interface Factory
    {
        AbstractPathResolver create(DirectoryService ds);
    }

    protected AbstractPathResolver(DirectoryService ds, IMapSIndex2SID sidx2sid,
            IMapSID2SIndex sid2sidx)
    {
        _ds = ds;
        _sidx2sid = sidx2sid;
        _sid2sidx = sid2sidx;
    }

    /**
     * @return The path where the OA locates. The first element in the returned list is the last
     * element of the path; the second element is the second last path element, and so on. This
     * convention is to simplify the implementation of this method.
     */
    @Nonnull
    protected abstract ResolvedPath resolve_(@Nonnull OA oa) throws SQLException;

    protected ResolvedPath makePath_(SIndex sidx, List<SOID> soids, List<String> elems)
    {
        return new ResolvedPath(_sidx2sid.get_(sidx), Lists.reverse(soids), Lists.reverse(elems));
    }

    /**
     * @return The SOID the Path refers to. Return null if the path is not found.
     */
    public @Nullable SOID resolve_(@Nonnull Path path) throws SQLException
    {
        SIndex sidx = _sid2sidx.getNullable_(path.sid());
        if (sidx == null) return null;

        OID oid = OID.ROOT;
        int i = 0;
        while (i < path.elements().length) {
            OID child = _ds.getChild_(sidx, oid, path.elements()[i]);
            if (child == null) {
                OA oa = _ds.getOA_(new SOID(sidx, oid));
                if (oa.isAnchor()) {
                    SOID soid = _ds.followAnchorNullable_(oa);
                    if (soid == null) return null;
                    sidx = soid.sidx();
                    oid = soid.oid();
                } else {
                    return null;
                }
            } else {
                oid = child;
                i++;
            }
        }

        return new SOID(sidx, oid);
    }
}

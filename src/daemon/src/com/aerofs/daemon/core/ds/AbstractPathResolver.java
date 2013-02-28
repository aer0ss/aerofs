/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.ds;

import com.aerofs.base.id.OID;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;

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
 *  o The above is true regardless of whether the root is a root store (there may be multiple root
 *    stores in a multi-user system).
 *
 * Also see IStores for store hierachy, which is related to but different from path hierarchy.
 *
 * N.B. implementations should be stateless and should not cache results. The classes they
 * depend on (DirectoryService and other classes) may cache state internally. A stateful
 * implementation would cause inconsistency unless cache coherence is explicitly maintained.
 */
public abstract class AbstractPathResolver
{
    /**
     * @return The path where the OA locates. The first element in the returned list is the last
     * element of the path; the second element is the second last path element, and so on. This
     * convention is to simplify the implementation of this method.
     */
    @Nonnull
    public abstract List<String> resolve_(@Nonnull OA oa) throws SQLException;

    /**
     * @return The SOID the Path refers to. Return null if the path is not found.
     */
    @Nullable
    public abstract SOID resolve_(@Nonnull Path path) throws SQLException;

    /**
     * Helper method to resolve a path into a SOID, following any anchors that may be present in the
     * path.
     *
     * @param ds       an instance of DirectoryService to be used for the resolution
     * @param sidx     the root store index that this path is refering to
     * @param path     the path to resolve
     * @param startAt  the path element to start the resolution from. E.g., if this is 1, the first
     *                 element in the path will be skipped.
     *
     * @return         null if the path is not found, or a new SOID corresponding to that path
     *
     * @throws SQLException
     */
    public static @Nullable SOID resolvePath_(DirectoryService ds, SIndex sidx, Path path, int startAt)
            throws SQLException
    {
        OID oid = OID.ROOT;
        int i = startAt;
        while (i < path.elements().length) {
            OID child = ds.getChild_(sidx, oid, path.elements()[i]);
            if (child == null) {
                OA oa = ds.getOA_(new SOID(sidx, oid));
                if (oa.isAnchor()) {
                    SOID soid = ds.followAnchorNullable_(oa);
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

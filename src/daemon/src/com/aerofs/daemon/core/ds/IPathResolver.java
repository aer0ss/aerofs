/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.ds;

import com.aerofs.lib.Path;
import com.aerofs.lib.id.SOID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.List;

/**
 * This interface defines path hierarchies, which is how paths correspond to objects.
 *
 * In single-user systems:
 *  o There is a single root store, The path to its root folder is the empty path.
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
 * N.B. implementations should be stateless and should not cache results. It is the classes they
 * depend on (DiectoryService and other classes) may cache state internally. A stateful
 * implementation would cause inconsistency unless cache coherience is explicitly maintained.
 */
public interface IPathResolver
{
    /**
     * @return The path where the OA locates. The first element in the returned list is the last
     * element of the path; the second element is the second last path element, and so on. This
     * convention is to simplify the implementation of this interface..
     */
    @Nonnull List<String> resolve_(@Nonnull OA oa) throws SQLException;

    /**
     * @return The SOID the Path refers to. Return null if the path is not found.
     */
    @Nullable SOID resolve_(@Nonnull Path path) throws SQLException;
}

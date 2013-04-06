package com.aerofs.daemon.core.store;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SIndex;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Set;

/**
 * This interface provide a high-level accessor to IStoreDatabase. Clients should use this interface
 * instead of referring to IStoreDatabse directly.
 *
 * General model of store hierarchy:
 *
 *  o Each store has zero or one or more parents.
 *  o Store S is a parent of store T iff. S contains an admitted Anchor object referring to T.
 *  o Root stores have zero parents.
 *
 * Additional constrains in single-user systems (See SingleuserStores):
 *
 *  o There is a single root store whose SID is derived from the user ID.
 *  o Each non-root store has one and only one parent store. This is maintained by the migration
 *    system.
 *
 * Also see IPathResolver for path hierachy, which is related to but different from store hierarchy.
 */
public interface IStores
{
    void init_() throws SQLException, IOException;

    /**
     * @pre the store is not present locally
     */
    void add_(SIndex sidx, Trans t) throws SQLException;

    /**
     * @pre the store is present locally, and the parent doesn't exist
     */
    void addParent_(SIndex sidx, SIndex sidxParent, Trans t) throws SQLException;

    /**
     * @pre the store is present locally, and the parent exists
     */
    void deleteParent_(SIndex sidx, SIndex sidxParent, Trans t) throws SQLException;

    /**
     * @return true if the store is a root store.
     *
     * Invariant: a root store has an empty parent list: isRoot(S) == getParents_(S).isEmpty()
     */
    boolean isRoot_(SIndex sidx) throws SQLException;

    /**
     * @return parents of the specified store.
     * @pre the store is present locally
     *
     * Invariant: a root store has an empty parent list: isRoot(S) == getParents_(S).isEmpty()
     */
    @Nonnull Set<SIndex> getParents_(SIndex sidx) throws SQLException;

    /**
     * @return direct children of the given store
     * @pre the store is present locally
     */
    @Nonnull Set<SIndex> getChildren_(SIndex sidx) throws SQLException;

    /**
     * @return the set of all the stores that are present locally
     */
    @Nonnull Set<SIndex> getAll_() throws SQLException;
}

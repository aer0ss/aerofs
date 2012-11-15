package com.aerofs.daemon.core.store;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.ex.ExAlreadyExist;
import com.aerofs.lib.id.SIndex;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Set;

/**
 * This is a wrapper around IStoreDatabase
 */
public interface IStores
{
    void init_() throws SQLException, ExAlreadyExist, IOException;

    /**
     * @param sidxParent set to {@code sidx} for root stores. See {@link IStores#getRoot_}.
     *
     * @pre the store is not present locally
     */
    void add_(SIndex sidx, @Nonnull SIndex sidxParent, Trans t) throws SQLException;

    /**
     * @pre the store is present locally
     */
    void setParent_(SIndex sidx, SIndex sidxParent, Trans t) throws SQLException;

    /**
     * @return the root store of the given path. For single-user systems, there is a single root
     * store, and the return value doesn't depend on the parameter; for multi-user systems, each
     * store is its own root, and the first element in the parameter identifies the SID in Base64
     * encoding.
     */
    @Nonnull SIndex getRoot_(Path path);

    /**
     * @return true if and only if getParent_(sidx).equals(sidx)
     *
     * There is a single root in single-user system, where multiple roots can exist in multi-user
     * systems.
     */
    boolean isRoot_(SIndex sidx);

    /**
     * @return the parent of the specified store.
     * @pre the store is present locally
     *
     * Invariant: the parent of a root store is itself.
     */
    @Nonnull SIndex getParent_(SIndex sidx) throws SQLException;

    /**
     * @return direct children of the given store
     * @pre the store is present locally
     */
    Set<SIndex> getChildren_(SIndex sidx) throws SQLException;

    /**
     * @return the set of all the stores that are present locally
     */
    Set<SIndex> getAll_() throws SQLException;
}

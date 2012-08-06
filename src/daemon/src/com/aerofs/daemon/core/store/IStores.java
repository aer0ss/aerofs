package com.aerofs.daemon.core.store;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SIndex;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.util.Set;

/**
 * This is a wrapper around IStoreDatabase
 */
public interface IStores
{
    /**
     * @param sidxParent set to {@code sidx} for the root store. TODO use null instead?
     *
     * @pre the store is not present locally
     */
    void add_(SIndex sidx, @Nonnull SIndex sidxParent, Trans t) throws SQLException;

    /**
     * @pre the store is present locally
     */
    void delete_(SIndex sidx, Trans t) throws SQLException;

    /**
     * @pre the store is present locally
     */
    void setParent_(SIndex sidx, SIndex sidxParent, Trans t) throws SQLException;

    /**
     * @return the store which points to itself as the parent
     */
    @Nonnull SIndex getRoot_();

    /**
     * @return {@code sidx} if {@code sidx} refers to the root store
     * @pre the store is present locally
     */
    @Nonnull SIndex getParent_(SIndex sidx) throws SQLException;

    /**
     * @return direct children of the given store
     * @pre the store is present locally
     */
    Set<SIndex> getChildren_(SIndex sidx) throws SQLException;

    Set<SIndex> getAll_() throws SQLException;
}

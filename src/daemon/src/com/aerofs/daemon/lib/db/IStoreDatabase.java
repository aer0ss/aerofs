package com.aerofs.daemon.lib.db;

import java.sql.SQLException;
import java.util.Set;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SIndex;

/**
 * Do not use this class directly. Use {@link com.aerofs.daemon.core.store.Stores} instead
 */
public interface IStoreDatabase
{
    Set<SIndex> getAll_() throws SQLException;

    /**
     * @return true if there is at least one store
     */
    boolean hasAny_() throws SQLException;

    String getName_(SIndex sidx) throws SQLException;

    void insert_(SIndex sidx, String name, Trans t) throws SQLException;

    void delete_(SIndex sidx, Trans t) throws SQLException;

    /**
     * @pre the parenthood relation doesn't exist
     */
    void insertParent_(SIndex sidx, SIndex sidxParent, Trans t) throws SQLException;

    /**
     * @pre the parenthood relation exists
     */
    void deleteParent_(SIndex sidx, SIndex sidxParent, Trans t) throws SQLException;

    Set<SIndex> getParents_(SIndex sidx) throws SQLException;

    Set<SIndex> getChildren_(SIndex sidx) throws SQLException;

    /**
     * Assert that a store exists. For debugging only. Foreign keys can replace this method in some
     * but not all situations.
     */
    void assertExists_(SIndex sidx) throws SQLException;
}

package com.aerofs.daemon.lib.db;

import com.aerofs.ids.DID;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.bf.BFOID;
import com.aerofs.lib.id.SIndex;

import javax.annotation.Nullable;
import java.sql.SQLException;

public interface ICollectorFilterDatabase
{
    void setCollectorFilter_(SIndex sidx, DID did, BFOID filter, Trans t) throws SQLException;

    void deleteCollectorFilter_(SIndex sidx, DID did, Trans t) throws SQLException;

    /**
     * @return null if not found
     */
    @Nullable BFOID getCollectorFilter_(SIndex sidx, DID did) throws SQLException;

    void deleteCollectorFiltersForStore_(SIndex sidx, Trans t) throws SQLException;
}

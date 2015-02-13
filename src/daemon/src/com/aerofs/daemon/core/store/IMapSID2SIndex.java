package com.aerofs.daemon.core.store;

import java.sql.SQLException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.SID;
import com.aerofs.lib.id.SIndex;

/**
 * This interface maintains a one-on-one mapping from SIDs to SIndexes. The mapping is guaranteed to
 * be persistent across store recreations.
 */
public interface IMapSID2SIndex
{
    /**
     * @return null if the store doesn't exist locally
     */
    @Nullable SIndex getNullable_(SID sid);

    /**
     * @pre the store exists locally
     */
    @Nonnull SIndex get_(SID sid);

    /**
     * @throws ExNotFound if the store doesn't exist locally
     */
    @Nonnull SIndex getThrows_(SID sid) throws ExNotFound;

    /**
     * Get the corresponding SIndex for a locally present or expelled SID
     */
    @Nullable SIndex getLocalOrAbsentNullable_(SID sid) throws SQLException;

    /**
     * Get the corresponding SIndex for SIDs that don't exist locally
     *
     * @pre the store doesn't exist locally.
     */
    @Nonnull SIndex getAbsent_(SID sid, Trans t) throws SQLException;
}

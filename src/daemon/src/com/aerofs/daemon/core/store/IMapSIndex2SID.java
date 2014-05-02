package com.aerofs.daemon.core.store;

import java.sql.SQLException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.aerofs.base.ex.ExNotFound;
import com.aerofs.base.id.SID;
import com.aerofs.lib.id.SIndex;

/**
 * This interface maintains a one-on-one mapping from SIndexes to SIDs. The mapping is guaranteed to
 * be persistent across store recreations.
 */
public interface IMapSIndex2SID
{
    /**
     * @return null if the store doesn't exist locally
     */
    @Nullable SID getNullable_(SIndex sidx);

    /**
     * @pre the store exists locally
     */
    @Nonnull SID get_(SIndex sidx);

    /**
     * @throws ExNotFound if the store doesn't exist locally
     */
    @Nonnull SID getThrows_(SIndex sidx) throws ExNotFound;

    /**
     * Get the corresponding SID for SIndics that don't exist locally
     *
     * @pre the store doesn't exist locally.
     */
    @Nonnull SID getAbsent_(SIndex sidx) throws SQLException;

    @Nonnull SID getLocalOrAbsent_(SIndex sidx) throws SQLException;
}

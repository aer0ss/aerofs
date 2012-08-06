package com.aerofs.daemon.core.store;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.SIndex;

/**
 * This is the interface that maintains in-memory mapping from SIndics to Store objects.
 */
public interface IMapSIndex2Store
{
    /**
     * @return the store object corresponding to the sidx, null if not found
     */
    @Nullable Store getNullable_(SIndex sidx);

    /**
     * @return the store object corresponding to the sidx. Assertion failure if not found.
     */
    @Nonnull Store get_(SIndex sidx);

    /**
     * @return always a valid store object corresponding to the sidx
     * @throws ExNotFound if there is no corresponding store
     */
    @Nonnull Store getThrows_(SIndex sidx) throws ExNotFound;
}

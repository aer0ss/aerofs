package com.aerofs.daemon.lib.db;

import com.aerofs.ids.DID;
import com.aerofs.daemon.core.collector.SenderFilterIndex;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.bf.BFOID;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.SIndex;

import java.sql.SQLException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

// TODO return iterators instead of collections for all methods

public interface ISenderFilterDatabase
{
    /**
     * add a new filter or update an existing filter
     */
    void setSenderFilter_(SIndex sidx, SenderFilterIndex sfidx, BFOID filter, Trans t)
            throws SQLException;

    /**
     * the filter must exist
     */
    void deleteSenderFilter_(SIndex sidx, SenderFilterIndex sfidx, Trans t) throws SQLException;

    /**
     * @return if not found, return null if sfidx != BASE or an all-zero filter if sfidx == BASE
     */
    @Nullable BFOID getSenderFilter_(SIndex sidx, SenderFilterIndex sfidx) throws SQLException;

    /**
     * @return all the filters whose indices are within the range of [from, to)
     */
    IDBIterator<BFOID> getSenderFilters_(SIndex sidx, SenderFilterIndex from, SenderFilterIndex to)
            throws SQLException;

    /**
     * @return BASE if no filter exists
     */
    @Nonnull SenderFilterIndex getSenderFilterGreatestIndex_(SIndex sidx) throws SQLException;

    /**
     * this method assumes there's always a index prio to sfidx
     */
    SenderFilterIndex getSenderFilterPreviousIndex_(SIndex sidx, SenderFilterIndex sfidx)
            throws SQLException;

    //////////////////////////////////////
    // sender devices
    //////////////////////////////////////

    /**
     * @return null if not found
     */
    SenderFilterIndex getSenderDeviceIndex_(SIndex sidx, DID did) throws SQLException;

    /**
     * add a new filter or update an existing filter
     */
    void setSenderDeviceIndex_(SIndex sidx, DID did, SenderFilterIndex sfidx, Trans t)
            throws SQLException;

    int getSenderDeviceIndexCount_(SIndex sidx, SenderFilterIndex sfidx) throws SQLException;

    void deleteSenderFiltersAndDevicesForStore_(SIndex sidx, Trans t) throws SQLException;
}

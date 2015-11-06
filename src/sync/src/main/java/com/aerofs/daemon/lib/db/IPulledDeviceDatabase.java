package com.aerofs.daemon.lib.db;

import java.sql.SQLException;

import com.aerofs.ids.DID;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SIndex;

/**
 * A persistent set of (SIndex, DID) pairs:
 * the DIDs pairs that have been pulled for SenderFilters about store SIndex
 * from this local device
 * @author markj
 *
 */
public interface IPulledDeviceDatabase
{
    /**
     * @return true if store w SIndex sidx has been pulled for kwlg from did
     */
    boolean contains_(SIndex sidx, DID did) throws SQLException;

    /**
     * add a device that has been pulled for knowledge about store sidx
     */
    void insert_(SIndex sidx, DID did, Trans t) throws SQLException;

    /**
     * delete the pulled devices associated with store indexed by sidx
     */
    void discardAllDevices_(SIndex sidx, Trans t) throws SQLException;
}

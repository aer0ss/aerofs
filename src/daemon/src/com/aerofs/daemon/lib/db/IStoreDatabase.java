package com.aerofs.daemon.lib.db;

import java.sql.SQLException;
import java.util.Collection;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SIndex;

public interface IStoreDatabase
{
    public static class StoreRow
    {
        public SIndex _sidx;
        public SIndex _sidxParent;
    }

    Collection<StoreRow> getAll_() throws SQLException;

    void add_(SIndex sidx, SIndex sidxParent, Trans t) throws SQLException;

    void setParent_(SIndex sidx, SIndex sidxParent, Trans t) throws SQLException;

    void delete_(SIndex sidx, Trans t) throws SQLException;

    /**
     * Do not use directly!
     * See {@link com.aerofs.daemon.core.store.MapSIndex2DeviceBitMap#getDeviceMapping_}
     */
    byte[] getDeviceMapping_(SIndex sidx) throws SQLException;

    /**
     * Do not use directly!
     * See {@link com.aerofs.daemon.core.store.MapSIndex2DeviceBitMap#addDevice_}
     */
    void setDeviceMapping_(SIndex sidx, byte mapping[], Trans t) throws SQLException;
}

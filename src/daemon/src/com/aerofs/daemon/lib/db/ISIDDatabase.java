package com.aerofs.daemon.lib.db;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.SID;
import com.aerofs.lib.id.SIndex;

import java.sql.SQLException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface ISIDDatabase
{
    /**
     * @return null if the SID is not found
     */
    @Nullable SIndex getSIndex_(SID sid) throws SQLException;

    @Nonnull SID getSID_(SIndex sidx) throws SQLException;

    /**
     * @return the SIndex of the newly created SID
     */
    @Nonnull SIndex insertSID_(SID sid, Trans t) throws SQLException;
}

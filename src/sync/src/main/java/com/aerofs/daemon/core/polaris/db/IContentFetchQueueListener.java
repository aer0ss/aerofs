package com.aerofs.daemon.core.polaris.db;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.OID;
import com.aerofs.lib.id.SIndex;

import java.sql.SQLException;

public interface IContentFetchQueueListener
{
    void onInsert_(SIndex sidx, OID oid, Trans t) throws SQLException;
}

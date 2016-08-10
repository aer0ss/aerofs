package com.aerofs.daemon.core;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.ids.OID;
import com.aerofs.lib.id.SIndex;

import java.sql.SQLException;

public interface IContentVersionListener
{
    void onSetVersion_(SIndex sidx, OID oid, long v, Trans t) throws SQLException;
    default void onContentUnavailable_(SIndex sidx, OID oid, Trans t) throws SQLException {}
}

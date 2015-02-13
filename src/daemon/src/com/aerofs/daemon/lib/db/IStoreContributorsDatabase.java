package com.aerofs.daemon.lib.db;

import com.aerofs.ids.DID;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.id.SIndex;

import java.sql.SQLException;
import java.util.Set;

/**
 * The "store contributors" database keeps track of DIDs that contribute to version vectors
 * for each store. It is mostly an accelearating structure to speed up construction of GetVersReply.
 */
public interface IStoreContributorsDatabase
{
    void addContributor_(SIndex sidx, DID did, Trans t) throws SQLException;

    Set<DID> getContributors_(SIndex sidx) throws SQLException;
}

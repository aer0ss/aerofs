package com.aerofs.daemon.core.status;

import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.Path;
import com.aerofs.lib.id.SOID;
import com.aerofs.proto.PathStatus.PBPathStatus.Sync;

import java.sql.SQLException;

public interface ISyncStatusPropagator
{
    default void addListener(ISyncStatusListener listener) {}

    default Sync getSync_(Path path) throws SQLException { return Sync.UNKNOWN; }

    default Sync getSync_(SOID soid) throws SQLException { return Sync.UNKNOWN; }

    default void updateSyncStatus_(SOID soid, boolean synced, Trans t) throws SQLException {}
}
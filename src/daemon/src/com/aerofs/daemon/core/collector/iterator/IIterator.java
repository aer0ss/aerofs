package com.aerofs.daemon.core.collector.iterator;

import java.sql.SQLException;

import com.aerofs.daemon.lib.db.ICollectorSequenceDatabase.OCIDAndCS;
import com.aerofs.daemon.lib.db.trans.Trans;

public interface IIterator
{
    /**
     * @return null if the iteration completes. otherwise the returned collector
     * sequence (CS) must be strictly monotonically increasing.
     */
    OCIDAndCS next_(Trans t) throws SQLException;

    void close_() throws SQLException;
}

/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.collector;

import com.aerofs.daemon.lib.db.ICollectorSequenceDatabase;
import com.aerofs.daemon.lib.db.ICollectorSequenceDatabase.OCIDAndCS;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.SIndex;

import javax.annotation.Nullable;
import java.sql.SQLException;

/**
 * Simple collector queue iterator for regular clients
 */
public class IteratorFullReplica extends AbstractIterator
{
    public IteratorFullReplica(ICollectorSequenceDatabase csdb, CollectorSkipRule csr, SIndex sidx)
    {
        super(csdb, csr, sidx);
    }

    @Override
    protected IDBIterator<OCIDAndCS> fetch_(@Nullable CollectorSeq csStart, int limit)
            throws SQLException
    {
        return _csdb.getCS_(_sidx, csStart, limit);
    }
}

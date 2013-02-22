/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.collector;

import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.ds.DirectoryService;
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
    public IteratorFullReplica(ICollectorSequenceDatabase csdb, NativeVersionControl nvc,
            DirectoryService ds, SIndex sidx)
    {
        super(csdb, nvc, ds, sidx);
    }

    @Override
    protected IDBIterator<OCIDAndCS> fetch_(@Nullable CollectorSeq cs, int limit)
            throws SQLException
    {
        return _csdb.getCS_(_sidx, cs, limit);
    }
}

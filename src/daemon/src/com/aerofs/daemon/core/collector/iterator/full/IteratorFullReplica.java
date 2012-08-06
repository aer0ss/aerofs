package com.aerofs.daemon.core.collector.iterator.full;

import java.sql.SQLException;

import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.collector.Common;
import com.aerofs.daemon.core.collector.iterator.IIterator;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.lib.db.ICollectorSequenceDatabase;
import com.aerofs.daemon.lib.db.ICollectorSequenceDatabase.OCIDAndCS;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;

class IteratorFullReplica implements IIterator
{
    private final ICollectorSequenceDatabase _csdb;
    private final NativeVersionControl _nvc;
    private final DirectoryService _ds;

    private final IDBIterator<OCIDAndCS> _dbiter;
    private final SIndex _sidx;

    IteratorFullReplica(ICollectorSequenceDatabase csdb, NativeVersionControl nvc,
            DirectoryService ds, IDBIterator<OCIDAndCS> dbiter, SIndex sidx)
    {
        _ds = ds;
        _csdb = csdb;
        _nvc = nvc;
        _dbiter = dbiter;
        _sidx = sidx;
    }

    @Override
    public OCIDAndCS next_(Trans t) throws SQLException
    {
        while (true) {
            if (!_dbiter.next_()) return null;
            OCIDAndCS ret = _dbiter.get_();
            SOCID socid = new SOCID(_sidx, ret._ocid);
            if (Common.shouldSkip_(_nvc, _ds, socid)) {
                _csdb.deleteCS_(ret._cs, t);
            } else {
                return ret;
            }
        }
    }

    @Override
    public void close_() throws SQLException
    {
        _dbiter.close_();
    }
}

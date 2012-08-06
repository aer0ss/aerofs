package com.aerofs.daemon.core.collector.iterator.full;

import java.sql.SQLException;

import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.collector.CollectorSeq;
import com.aerofs.daemon.core.collector.iterator.IIterator;
import com.aerofs.daemon.core.collector.iterator.IIteratorFactory;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.lib.db.ICollectorSequenceDatabase;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.id.SIndex;

public class IteratorFactoryFullReplica implements IIteratorFactory
{
    private final ICollectorSequenceDatabase _csdb;
    private final NativeVersionControl _nvc;
    private final DirectoryService _ds;

    private final SIndex _sidx;

    public IteratorFactoryFullReplica(ICollectorSequenceDatabase csdb, NativeVersionControl nvc,
            DirectoryService ds, SIndex sidx)
    {
        assert Cfg.isFullReplica();

        _ds = ds;
        _csdb = csdb;
        _nvc = nvc;
        _sidx = sidx;
    }

    @Override
    public IIterator newIterator_(CollectorSeq cs) throws SQLException
    {
        return new IteratorFullReplica(_csdb, _nvc, _ds, _csdb.getAllCS_(_sidx, cs), _sidx);
    }
}

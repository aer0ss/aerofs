package com.aerofs.daemon.core.collector.iterator.partial;

import java.sql.SQLException;

import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.collector.CollectorSeq;
import com.aerofs.daemon.core.collector.iterator.IIterator;
import com.aerofs.daemon.core.collector.iterator.IIteratorFactory;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.store.Store;
import com.aerofs.daemon.lib.db.ICollectorSequenceDatabase;
import com.aerofs.lib.cfg.Cfg;

public class IteratorFactoryPartialReplica implements IIteratorFactory
{
    private final ICollectorSequenceDatabase _csdb;
    private final NativeVersionControl _nvc;
    private final DirectoryService _ds;
    private final Store _s;

    public IteratorFactoryPartialReplica(ICollectorSequenceDatabase csdb, NativeVersionControl nvc,
            DirectoryService ds, Store s)
    {
        assert !Cfg.isFullReplica();
        _ds = ds;
        _csdb = csdb;
        _nvc = nvc;
        _s = s;
    }

    @Override
    public IIterator newIterator_(CollectorSeq cs) throws SQLException
    {
        return new IteratorPartialReplica(_csdb, _nvc, _ds, _s, cs);
    }
}

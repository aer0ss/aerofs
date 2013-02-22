/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.collector;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.store.Store;
import com.aerofs.daemon.lib.db.ICollectorSequenceDatabase;
import com.aerofs.daemon.lib.db.ICollectorSequenceDatabase.OCIDAndCS;
import com.aerofs.lib.db.IDBIterator;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.sql.SQLException;


/**
 * Simple collector queue iterator for clients with partial replica (old cloud server)
 */
public class IteratorPartialReplica extends AbstractIterator
{
    private final static Logger l = Loggers.getLogger(IteratorPartialReplica.class);

    private final Store _s;

    private boolean _meta;

    /**
     * To present a strictly ascending sequence of CS while ensuring that META is collected
     * before other components we need to artificially offset CS for META items
     */
    static private CollectorSeq DBCS2Meta(CollectorSeq cs)
    {
        long l = cs.getLong() + Long.MIN_VALUE;
        assert l < 0;
        return new CollectorSeq(l);
    }

    IteratorPartialReplica(ICollectorSequenceDatabase csdb, NativeVersionControl nvc,
            DirectoryService ds, Store s)
    {
        super(csdb, nvc, ds, s.sidx());
        _s = s;
        _meta = true;
    }

    @Override
    public @Nullable OCIDAndCS current_()
    {
        OCIDAndCS occs = super.current_();
        return _meta && occs != null ? new OCIDAndCS(occs._ocid, DBCS2Meta(occs._cs)) : occs;
    }

    @Override
    protected boolean hasNext_() throws SQLException
    {
        return (super.hasNext_() && (_meta || !_s.isOverQuota_(0))) || switchToNonMeta_();
    }

    private boolean switchToNonMeta_() throws SQLException
    {
        switch_();
        if (!_meta) return false;
        _meta = false;
        if (_s.isOverQuota_(0)) {
            l.debug("over quota before non-meta. stop");
            return false;
        } else {
            l.debug("switch from meta to non-meta");
            return hasNext_();
        }
    }

    @Override
    protected IDBIterator<OCIDAndCS> fetch_(@Nullable CollectorSeq cs, int limit)
            throws SQLException
    {
        return _meta ? _csdb.getMetaCS_(_s.sidx(), cs, limit)
                : _csdb.getNonMetaCS_(_s.sidx(), cs, limit);
    }
}

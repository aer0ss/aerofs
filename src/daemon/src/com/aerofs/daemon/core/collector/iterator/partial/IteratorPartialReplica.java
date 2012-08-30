package com.aerofs.daemon.core.collector.iterator.partial;

import java.sql.SQLException;

import com.aerofs.daemon.lib.db.trans.Trans;
import org.apache.log4j.Logger;

import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.collector.CollectorSeq;
import com.aerofs.daemon.core.collector.Common;
import com.aerofs.daemon.core.collector.iterator.IIterator;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.store.Store;
import com.aerofs.daemon.lib.db.ICollectorSequenceDatabase;
import com.aerofs.daemon.lib.db.ICollectorSequenceDatabase.OCIDAndCS;
import com.aerofs.lib.Util;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.SOCID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

class IteratorPartialReplica implements IIterator
{
    private final static Logger l = Util.l(IteratorPartialReplica.class);

    private final ICollectorSequenceDatabase _csdb;
    private final NativeVersionControl _nvc;
    private final DirectoryService _ds;
    private final Store _s;

    private boolean _meta;
    private IDBIterator<OCIDAndCS> _dbiter;

    static private CollectorSeq DBCS2Meta(CollectorSeq cs)
    {
        long l = cs.getLong() + Long.MIN_VALUE;
        assert l < 0;
        return new CollectorSeq(l);
    }

    static private CollectorSeq MetaCS2DB(@Nonnull CollectorSeq cs)
    {
        return new CollectorSeq(cs.getLong() - Long.MIN_VALUE);
    }

    static private boolean isMetaCS(@Nonnull CollectorSeq cs)
    {
        return cs.getLong() < 0;
    }

    IteratorPartialReplica(ICollectorSequenceDatabase csdb, NativeVersionControl nvc,
            DirectoryService ds, Store s, @Nullable CollectorSeq cs)
            throws SQLException
    {
        _ds = ds;
        _csdb = csdb;
        _nvc = nvc;
        _s = s;

        if (cs == null) {
            _meta = true;
            _dbiter = _csdb.getAllMetaCS_(_s.sidx(), null);
        } else if (isMetaCS(cs)) {
            _meta = true;
            _dbiter = _csdb.getAllMetaCS_(_s.sidx(), MetaCS2DB(cs));
        } else {
            _meta = false;
            _dbiter = _csdb.getAllNonMetaCS_(_s.sidx(), cs);
        }
    }

    /**
     * always return metadata before non-metadata. may stop while iterating
     * non-metadata if the store is over quota
     */
    @Override
    public OCIDAndCS next_(Trans t) throws SQLException
    {
        while (true) {
            if (!_dbiter.next_()) {
                if (_meta) {
                    _meta = false;
                    if (_s.isOverQuota_(0)) {
                        l.info("over quota before non-meta. stop");
                        return null;
                    } else {
                        l.info("switch from meta to non-meta");
                        _dbiter.close_();
                        _dbiter = _csdb.getAllNonMetaCS_(_s.sidx(), null);
                        continue;
                    }
                } else {
                    return null;
                }
            }

            OCIDAndCS ret = _dbiter.get_();
            assert ret._ocid.cid().isMeta() == _meta;

            SOCID socid = new SOCID(_s.sidx(), ret._ocid);
            if (Common.shouldSkip_(_nvc, _ds, socid)) {
                _csdb.deleteCS_(ret._cs, t);
            } else if (_meta) {
                return new OCIDAndCS(ret._ocid, DBCS2Meta(ret._cs));
            } else if (_s.isOverQuota_(0)) {
                l.info("over quota during non-meta. stop");
                return null;
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

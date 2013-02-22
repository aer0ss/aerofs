/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.collector;

import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.lib.db.ICollectorSequenceDatabase;
import com.aerofs.daemon.lib.db.ICollectorSequenceDatabase.OCIDAndCS;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.OCID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.google.common.collect.Lists;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.ArrayList;

/**
 * Iterator over the collector queue for a single store
 *
 * The iterator is guaranteed to go in increasing {@link CollectorSeq} order, to skip (and cleanup)
 * OCIDs that where successfully collected
 *
 * Because the collector queue is append-only and we only iterate it in ascending CollectorSeq order
 * we can safely cache the values fetched from the DB.
 * This class leverages this insight to optimize DB access patterns, with good results:
 * An aliasing stress test, notable for causing many Collector restarts, was spending a solid 10% of
 * its CPU time fetching the collector data over and over again before the refactoring and that time
 * dropped below .3% with this optimization
 *
 * The actual fetch operation is delegated to the concrete subclasses
 */
public abstract class AbstractIterator
{
    private final DirectoryService _ds;
    private final NativeVersionControl _nvc;
    protected final ICollectorSequenceDatabase _csdb;

    protected final SIndex _sidx;

    private @Nullable OCIDAndCS _last;
    private int _idx;
    private int _discardable;
    private final ArrayList<OCIDAndCS> _seq = Lists.newArrayList();

    // batch size used by the incremental fetch
    private final int FETCH_SIZE = 100;

    public AbstractIterator(ICollectorSequenceDatabase csdb, NativeVersionControl nvc,
            DirectoryService ds, SIndex sidx)
    {
        _ds = ds;
        _nvc = nvc;
        _csdb = csdb;
        _sidx = sidx;
    }

    /**
     * @return whether the iteration has been started
     */
    public boolean started()
    {
        return _last != null;
    }

    /**
     * Reset the iterator
     *
     * {@link #started} will return false after calling this method until {@link #next_} is called
     */
    public void reset_()
    {
        discard_();
        _last = null;
        _idx = 0;
    }

    /**
     * @return the current item, null if the iterator was not started
     */
    public @Nullable OCIDAndCS current_()
    {
        return _last;
    }

    /**
     * helper for get_()._cs that handles get_() == null
     */
    public final @Nullable CollectorSeq cs_()
    {
        OCIDAndCS occs = current_();
        return occs != null ? occs._cs : null;
    }

    /**
     * Fetch the next item from the sequence
     *
     * @return false if the end of the queue was reached
     */
    public boolean next_(Trans t) throws SQLException
    {
        while (hasNext_()) {
            _last = _seq.get(_idx++);
            if (skip_(_last._ocid)) {
                ++_discardable;
                // TODO: batch CS deletion whenever possible (move to discard_)
                _csdb.deleteCS_(_last._cs, t);
            } else {
                discard_();
                return true;
            }
        }
        _last = null;
        return false;
    }

    private void discard_()
    {
        if (_discardable == 0) return;
        // sigh, why this is considered better than making removeRange() public is beyond me...
        _seq.subList(_idx - _discardable, _idx).clear();
        _idx -= _discardable;
        _discardable = 0;
    }

    protected boolean hasNext_() throws SQLException
    {
        return _idx < _seq.size() || fetchMore_();
    }

    /**
     * pseudo-reset used by partial replica iterators to switch from meta to content iteration
     *
     * we need to clear the cached sequence but keep the current value around otherwise we might
     * mislead the calling code into thinking we did not start the iteration yet
     */
    protected void switch_()
    {
        discard_();
        _seq.clear();
        _idx = 0;
    }

    protected abstract IDBIterator<OCIDAndCS> fetch_(@Nullable CollectorSeq cs, int limit)
            throws SQLException;

    /**
     * @return true if the component can be skipped and removed from the queue
     */
    private boolean skip_(OCID ocid) throws SQLException
    {
        return Common.shouldSkip_(_nvc, _ds, new SOCID(_sidx, ocid));
    }

    /**
     * Fetch a batch of item from the collector queue
     * @return true if items where fetched, false if the end of the queue was reached
     */
    private boolean fetchMore_() throws SQLException
    {
        // TODO: shrink _seq if it becomes too large (NB: would then have to clear it on reset_)
        assert _idx == _seq.size();
        IDBIterator<OCIDAndCS> it = fetch_(cs_(), FETCH_SIZE);
        _seq.ensureCapacity(_seq.size() + FETCH_SIZE);
        try {
            while (it.next_()) {
                _seq.add(it.get_());
            }
        } finally {
            it.close_();
        }
        return _idx < _seq.size();
    }

    @Override
    public String toString()
    {
        return "CSI(" + _sidx + "," + _last + ")";
    }
}

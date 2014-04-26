/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.collector;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.ICollectorStateDatabase;
import com.aerofs.daemon.lib.db.ICollectorSequenceDatabase;
import com.aerofs.daemon.lib.db.ICollectorSequenceDatabase.OCIDAndCS;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.google.common.collect.Lists;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.sql.SQLException;
import java.util.ArrayList;

import static com.google.common.base.Preconditions.checkNotNull;

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
class CollectorIterator
{
    private static final Logger l = Loggers.getLogger(CollectorIterator.class);

    // batch size used by the incremental fetch
    static final int FETCH_SIZE = 100;

    // keep the memory usage of the cached data reasonable (OCIDAndCS has a 288 bytes footprint)
    // NB: The in-memory footprint for OCIDAndCS could be brought under 256 *bits* without Java's
    // retarded boxing... I miss good old C/C++
    static final int SHRINK_THRESHOLD = 2000;

    private final Factory _f;

    private final SIndex _sidx;

    // The collector queue cache. See the class-level comment.
    private final ArrayList<OCIDAndCS> _seq = Lists.newArrayList();

    private boolean _isContentIncluded;

    private @Nullable OCIDAndCS _current;

    // An index in {@link #_seq} pointing to the item to be iterated over next
    private int _next;

    private int _discardable;

    // Whether to clear {@link #_seq} on the next {@link #reset_()} call
    private boolean _clearOnReset;

    public static class Factory {

        private final ICollectorSequenceDatabase _csdb;
        private final CollectorSkipRule _csr;
        private final ICollectorStateDatabase _cidb;

        @Inject
        public Factory(ICollectorSequenceDatabase csdb, CollectorSkipRule csr,
                ICollectorStateDatabase cidb)
        {
            _csdb = csdb;
            _cidb = cidb;
            _csr = csr;
        }

        CollectorIterator create_(SIndex sidx)
                throws SQLException
        {
            return new CollectorIterator(this, sidx);
        }
    }

    private CollectorIterator(Factory f, SIndex sidx)
            throws SQLException
    {
        _f = f;
        _sidx = sidx;
        _isContentIncluded = f._cidb.isCollectingContent_(sidx);
    }

    /**
     * @return whether the iteration has been started
     */
    boolean started_()
    {
        return _current != null;
    }

    /**
     * Reset the iterator
     *
     * {@link #started_} will return false after calling this method until {@link #next_} is called
     */
    void reset_()
    {
        // if we had to discard some cached values we need to force a re-fetch...
        if (_clearOnReset) _seq.clear();
        _clearOnReset = false;
        _current = null;
        _next = 0;
    }

    /**
     * @return the current item, null if the iterator was not started
     */
    @Nullable OCIDAndCS currentNullable_()
    {
        return _current;
    }

    /**
     * @return the current item
     */
    @Nonnull OCIDAndCS current_()
    {
        return checkNotNull(currentNullable_());
    }

    /**
     * a shortcut for current_()._cs
     */
    @Nonnull CollectorSeq cs_()
    {
        return current_()._cs;
    }

    /**
     * Fetch the next item from the sequence
     *
     * @return false if the end of the queue was reached
     */
    boolean next_(Trans t) throws SQLException
    {
        _discardable = 0;

        while (hasNext_()) {
            _current = _seq.get(_next);
            if (!_f._csr.shouldSkip_(new SOCID(_sidx, _current._ocid))) break;
            // TODO: batch CS deletion whenever possible
            _f._csdb.deleteCS_(current_()._cs, t);
            ++_discardable;
            ++_next;
        }

        // clear items
        // NB: noop if discardable == 0
        // NB: the max is needed to handle _seq being cleared under us by fetchMore_
        _seq.subList(_next - _discardable, _next).clear();
        _next -= _discardable;

        if (_next < _seq.size()) {
            // found a collectable item, move index past it
            ++_next;
        } else {
            // reached end of queue, reset current item
            _current = null;
        }
        return _current != null;
    }

    /**
     * Return whether there are items on the queue after the {@link #_next} pointer. Fetch more
     * items into the cache as necessary.
     */
    private boolean hasNext_() throws SQLException
    {
        return _next < _seq.size() || fetchMore_();
    }

    private void clearCache_()
    {
        _seq.clear();
        _discardable = 0;
        _next = 0;
        // discarding elements require refetching from the start on reset_
        _clearOnReset = true;
    }

    /**
     * Include content components for future iterations. Callers to this method should call
     * {@link com.aerofs.daemon.core.store.Store#resetCollectorFiltersForAllDevices_}
     * to reset collector filters. See that method's comments for more info.
     *
     * @return whether the content was excluded before this call
     */
    boolean includeContent_(Trans t)
            throws SQLException
    {
        if (_isContentIncluded) return false;
        l.info("include content for store {}", _sidx);
        clearCache_();
        _f._cidb.setCollectingContent_(_sidx, true, t);

        // Set the flag, and roll it back if the transaction fails.
        _isContentIncluded = true;
        t.addListener_(new AbstractTransListener() {
            @Override
            public void aborted_()
            {
                _isContentIncluded = false;
            }
        });

        return true;
    }

    /**
     * Exclude content components for future iterations.
     *
     * @return whether the content was included before this call
     */
    boolean excludeContent_(Trans t)
            throws SQLException
    {
        if (!_isContentIncluded) return false;
        l.info("exclude content for store {}", _sidx);
        clearCache_();
        _f._cidb.setCollectingContent_(_sidx, false, t);

        // Set the flag, and roll it back if the transaction fails.
        _isContentIncluded = false;
        t.addListener_(new AbstractTransListener() {
            @Override
            public void aborted_()
            {
                _isContentIncluded = true;
            }
        });

        return true;
    }

    /**
     * Fetch a batch of item from the collector queue
     * @return true if items where fetched, false if the end of the queue was reached
     */
    private boolean fetchMore_() throws SQLException
    {
        assert _next == _seq.size();

        // shrink _seq if it becomes too large
        if (_seq.size() > SHRINK_THRESHOLD) clearCache_();

        CollectorSeq cs = _current != null ? _current._cs : null;
        IDBIterator<OCIDAndCS> it = _isContentIncluded ?
                _f._csdb.getCS_(_sidx, cs, FETCH_SIZE) :
                _f._csdb.getMetaCS_(_sidx, cs, FETCH_SIZE);
        _seq.ensureCapacity(_seq.size() + FETCH_SIZE);
        try {
            while (it.next_()) {
                _seq.add(it.get_());
            }
        } finally {
            it.close_();
        }
        l.debug("fetch {} {} : {}", cs, FETCH_SIZE, _seq.size() - _next);
        return _next < _seq.size();
    }

    @Override
    public String toString()
    {
        return "CSI(" + _sidx + "," + _current + ")";
    }
}

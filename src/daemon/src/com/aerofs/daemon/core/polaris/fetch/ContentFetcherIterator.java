/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.fetch;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.OID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.polaris.db.CentralVersionDatabase;
import com.aerofs.daemon.core.polaris.db.ContentFetchQueueDatabase;
import com.aerofs.daemon.core.polaris.db.ContentFetchQueueDatabase.OIDAndFetchIdx;
import com.aerofs.daemon.core.polaris.db.RemoteContentDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.Lists;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.sql.SQLException;
import java.util.ArrayList;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 *
 */
public class ContentFetcherIterator
{
    private final static Logger l = Loggers.getLogger(ContentFetcherIterator.class);

    // batch size used by the incremental fetch
    static final int FETCH_SIZE = 100;

    // keep the memory usage of the cached data reasonable
    static final int SHRINK_THRESHOLD = 2000;

    private final Factory _f;

    private final SIndex _sidx;

    // The collector queue cache. See the class-level comment.
    private final ArrayList<OIDAndFetchIdx> _seq = Lists.newArrayList();

    private @Nullable OIDAndFetchIdx _current;

    // An index in {@link #_seq} pointing to the item to be iterated over next
    private int _next;

    private int _discardable;

    // Whether to clear {@link #_seq} on the next {@link #reset_()} call
    private boolean _clearOnReset;

    public static class Factory {
        private final DirectoryService _ds;
        private final CentralVersionDatabase _cvdb;
        private final RemoteContentDatabase _rcdb;
        private final ContentFetchQueueDatabase _cfqdb;

        @Inject
        public Factory(DirectoryService ds, RemoteContentDatabase rcdb,
                CentralVersionDatabase cvdb, ContentFetchQueueDatabase cfqdb)
        {
            _ds = ds;
            _rcdb = rcdb;
            _cvdb = cvdb;
            _cfqdb = cfqdb;
        }

        ContentFetcherIterator create_(SIndex sidx)
        {
            return new ContentFetcherIterator(this, sidx);
        }
    }

    private ContentFetcherIterator(Factory f, SIndex sidx)
    {
        _f = f;
        _sidx = sidx;
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
    @Nullable OID currentNullable_()
    {
        return _current;
    }

    /**
     * @return the current item
     */
    @Nonnull OID current_()
    {
        return checkNotNull(currentNullable_());
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
            OA oa = _f._ds.getOANullable_(new SOID(_sidx, _current));
            // do not try to fetch content if OA is missing but keep entry in fetch queue
            // to account for meta buffering
            if (oa != null) {
                if (!oa.isExpelled() && shouldFetch_(_current)) break;
                _f._cfqdb.remove_(_sidx, _current, t);
            }
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

    private boolean shouldFetch_(OID oid) throws SQLException
    {
        Long v = _f._cvdb.getVersion_(_sidx, oid);
        return _f._rcdb.hasRemoteChanges_(_sidx, oid, v != null ? v : 0);
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
     * Fetch a batch of item from the collector queue
     * @return true if items where fetched, false if the end of the queue was reached
     */
    private boolean fetchMore_() throws SQLException
    {
        assert _next == _seq.size();

        // shrink _seq if it becomes too large
        if (_seq.size() > SHRINK_THRESHOLD) clearCache_();

        _seq.ensureCapacity(_seq.size() + FETCH_SIZE);
        long idx = _current != null ? _current.idx : 0;
        try (IDBIterator<OIDAndFetchIdx> it = _f._cfqdb.list_(_sidx, idx)) {
            for (int i = 0; i < FETCH_SIZE && it.next_(); ++i) {
                _seq.add(it.get_());
            }
        }
        l.debug("fetch {} {} : {}", idx, FETCH_SIZE, _seq.size() - _next);
        return _next < _seq.size();
    }

    @Override
    public String toString()
    {
        return "CSI(" + _sidx + "," + _current + ")";
    }
}

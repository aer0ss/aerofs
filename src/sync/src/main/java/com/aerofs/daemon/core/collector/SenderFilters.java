package com.aerofs.daemon.core.collector;

import java.sql.SQLException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.aerofs.base.Loggers;
import com.aerofs.ids.DID;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.google.inject.Inject;
import org.slf4j.Logger;

import com.aerofs.daemon.lib.db.ISenderFilterDatabase;
import com.aerofs.lib.Util;
import com.aerofs.lib.bf.BFOID;
import com.aerofs.lib.db.IDBIterator;
import com.aerofs.ids.OID;
import com.aerofs.lib.id.SIndex;

import static com.aerofs.daemon.core.collector.SenderFilterIndex.BASE;
import static com.google.common.base.Preconditions.checkState;

public class SenderFilters
{
    private static final Logger l = Loggers.getLogger(SenderFilters.class);
    private final ISenderFilterDatabase _sfdb;
    private final TransManager _tm;

    private final SIndex _sidx;

    // these are the write-through cache of the last filter in the db
    private @Nonnull SenderFilterIndex _sfidxLast;
    private @Nonnull BFOID _filterLast;
    private long _updateSeq;
    private boolean _barrier;

    public static class Factory
    {
        private final ISenderFilterDatabase _sfdb;
        private final TransManager _tm;

        @Inject
        public Factory(TransManager tm, ISenderFilterDatabase sfdb)
        {
            _tm = tm;
            _sfdb = sfdb;
        }

        public SenderFilters create_(SIndex sidx) throws SQLException
        {
            return new SenderFilters(_sfdb, _tm, sidx);
        }
    }

    private SenderFilters(ISenderFilterDatabase sfdb, TransManager tm, SIndex sidx)
            throws SQLException
    {
        _sfdb = sfdb;
        _tm = tm;
        _sidx = sidx;
        _updateSeq = Util.rand().nextLong();
        _sfidxLast = _sfdb.getSenderFilterGreatestIndex_(_sidx);
        BFOID filterLast = _sfdb.getSenderFilter_(_sidx, _sfidxLast);
        assert filterLast != null : _sidx + " " + _sfidxLast;
        _filterLast = filterLast;
    }

    /**
     * N.B. only VersionAssistant should call this method
     */
    public void objectUpdated_(OID oid, Trans t) throws SQLException
    {
        if (_filterLast.add_(oid)) {
            ++_updateSeq;
            _sfdb.setSenderFilter_(_sidx, _sfidxLast, _filterLast, t);
        } else if (_barrier) {
            // prevent last filter from being cleared
            ++_updateSeq;
        }
        _barrier = false;
    }

    public static class SenderFilterAndIndex {
        final public BFOID _filter;             // mustn't be null
        final public SenderFilterIndex _sfidx;  // mustn't be null
        final public long _updateSeq;           // see update_() for its purpose

        SenderFilterAndIndex(BFOID filter, SenderFilterIndex sfidx,
                long updateSeq)
        {
            assert filter != null && sfidx != null;
            _filter = filter;
            _sfidx = sfidx;
            _updateSeq = updateSeq;
        }
    }

    /**
     * @param fromBase true if the remote peer knows it needs all filters from
     * BASE onward, false if it can accept the filter at its expected
     * SenderFilterIndex onward.
     * @return the filter and filter index associated with did. Return the
     * union of BASE filter onward if fromBase so the remote can collect content
     * again. Return null if the filter is empty.
     */
    public @Nullable SenderFilterAndIndex get_(DID did, boolean fromBase) throws SQLException
    {
        // The SenderFilterIndex associated with the given DID
        SenderFilterIndex sfidxForDID = _sfdb.getSenderDeviceIndex_(_sidx, did);
        if (sfidxForDID == null) sfidxForDID = BASE;

        // The SenderFilterIndex base from which to union filters
        SenderFilterIndex sfidxFilter = fromBase ? BASE : sfidxForDID;

        BFOID ret;
        if (sfidxFilter.equals(_sfidxLast)) {
            // a shortcut for common cases
            checkState(sfidxFilter.equals(sfidxForDID), "{} {} {} {} {} {}",
                    _sidx, did, fromBase, sfidxFilter, sfidxForDID, _sfidxLast);
            ret = _filterLast;
        } else {
            ret = new BFOID(_filterLast);
            // TODO cache the last few filters
            IDBIterator<BFOID> iter = _sfdb.getSenderFilters_(_sidx, sfidxFilter, _sfidxLast);
            try {
                while (iter.next_()) ret.union_(iter.get_());
            } finally {
                iter.close_();
            }
        }

        if (ret.isEmpty_()) return null;

        // Draw a line in the sand
        // Any subsequent version update will cause a bump to updateSeq, even if the bloom
        // filter is left unchanged. This is necessary to avoid race conditions that leave
        // fresh ticks uncollectable due to over-eager BF merging, e.g.
        //
        // 1. create OA for o1: bump META tick
        //    -> add o1 to last filter
        // 2. GetVers from d1
        // 3. create CA for o1: bump CONTENT tick
        //    -> o1 already in last filter, seq number unchanged
        // 4. UpdateSenderFilter from d1
        //    -> seq number match, merge filters
        // 5. GetVers from d1
        //    -> empty filter prevents CONTENT tick from being collected  <=================
        //
        // NB: this is a tactical fix, longer term the lifecycle of Sender Filters should be
        // re-evaluated. They are only needed because we propagate KMLs...
        _barrier = true;

        return new SenderFilterAndIndex(ret, sfidxForDID, _updateSeq);
    }

    /**
     * delete the sfidxOld'th filter and merge it into the previous filer,
     * create a new filter for the device if needed, and use this filter as the
     * latest.
     *
     * the request is ignored if the specified index or the update sequence
     * doesn't match. Matching the specified index is needed to prevent us
     * from deleting filters that haven't been received by the peer in the event
     * of duplicate or out of order packets. Matching the update sequence is
     * needed to make sure since the filter is sent to the peer until this
     * update request there's no update; otherwise the peer would miss them.
     */
    public void update_(final DID did, final SenderFilterIndex sfidxOld, final long updateSeq)
        throws SQLException
    {
        if (updateSeq != _updateSeq) return;

        ////////
        // test whether to merge the old filter to the previous one

        boolean merge;
        if (sfidxOld.equals(BASE)) {
            // never delete the base filter
            merge = false;
        } else if (sfidxOld.equals(_sfidxLast) && _filterLast.isEmpty_()) {
            // never delete the last filter if it is empty
            merge = false;
        } else {
            // ignore the request if the provided index doesn't match the record
            if (!sfidxOld.equals(_sfdb.getSenderDeviceIndex_(_sidx, did))) {
                return;
            }

            int count = _sfdb.getSenderDeviceIndexCount_(_sidx, sfidxOld);
            assert count >= 1;
            merge = count == 1;
        }

        ////////
        // create a new filter if the last filter is not empty. this is to
        // exclude the last filter next time we send filters to the peer.
        //
        // TODO create only if _filterLast contains N or more changes

        boolean create;
        SenderFilterIndex sfidxNew;
        BFOID filterNew;
        if (_filterLast.isEmpty_()) {
            create = false;
            sfidxNew = _sfidxLast;
            filterNew = _filterLast;
        } else {
            create = true;
            sfidxNew = _sfidxLast.plusOne();
            filterNew = new BFOID();
        }

        l.debug("update 4 {} {} {} merge {} create {} sfidxNew {}",
                _sidx, did, sfidxOld, merge, create, sfidxNew);

        try (Trans t = _tm.begin_()) {
            if (merge) {
                SenderFilterIndex sfidxPrev = _sfdb
                        .getSenderFilterPreviousIndex_(_sidx, sfidxOld);
                l.debug("merge 4 {} {}<-{}", _sidx, sfidxPrev, sfidxOld);
                BFOID filterPrev = _sfdb.getSenderFilter_(_sidx, sfidxPrev);
                BFOID filterOld = _sfdb.getSenderFilter_(_sidx, sfidxOld);
                if (filterPrev.union_(filterOld)) {
                    _sfdb.setSenderFilter_(_sidx, sfidxPrev, filterPrev, t);
                }
                _sfdb.deleteSenderFilter_(_sidx, sfidxOld, t);
            }

            if (create) _sfdb.setSenderFilter_(_sidx, sfidxNew, filterNew, t);

            if (!sfidxNew.equals(sfidxOld)) // i.e. sfidxOld == _sfidxLast
                _sfdb.setSenderDeviceIndex_(_sidx, did, sfidxNew, t);

            // for each sfidx there must be a corresponding filter
            assert _sfdb.getSenderFilter_(_sidx, sfidxNew) != null;

            t.commit_();
        }

        if (create) {
            _sfidxLast = sfidxNew;
            _filterLast = filterNew;
        }
    }

    public void deletePersistentData_(Trans t)
            throws SQLException
    {
        _sfdb.deleteSenderFiltersAndDevicesForStore_(_sidx, t);

        // TODO (MJ) after this action the object is effectively unusable.  I could add a boolean
        // and assert no methods are called after deletePersistentData_?
        // I really wish Java had destructors...
    }
}

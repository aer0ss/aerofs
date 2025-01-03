package com.aerofs.daemon.core.collector;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExTimeout;
import com.aerofs.daemon.core.CoreExponentialRetry;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.ex.ExNoAvailDevice;
import com.aerofs.daemon.core.ex.ExWrapped;
import com.aerofs.daemon.core.polaris.db.ContentFetchQueueDatabase.OIDAndFetchIdx;
import com.aerofs.daemon.core.polaris.fetch.ContentFetcherIterator;
import com.aerofs.daemon.core.tc.ITokenReclamationListener;
import com.aerofs.daemon.core.transfers.download.IContentDownloads;
import com.aerofs.daemon.core.transfers.download.IDownloadCompletionListener;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.ICollectorFilterDatabase;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.daemon.lib.exception.ExStreamInvalid;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.lib.IDumpStatMisc;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.Util;
import com.aerofs.lib.bf.BFOID;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.lib.sched.ExponentialRetry;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.PrintStream;
import java.sql.SQLException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;

public class ContentFetcher implements IDumpStatMisc
{
    private static final Logger l = Loggers.getLogger(ContentFetcher.class);

    private final SIndex _sidx;
    private final CollectorFilters _cfs;
    private final ContentFetcherIterator _it;
    private int _startSeq;
    private int _downloads;     // # current downloads initiated by the collector
    private long _backoffInterval;
    private boolean _backoffScheduled;

    private boolean _stopped;  // sometimes shared folder disappears during exp retry

    public void stop_() {
        _stopped = true;
    }

    public static class Factory
    {
        private final CoreScheduler _sched;
        private final ICollectorFilterDatabase _cfdb;
        private final TransManager _tm;
        private final ExponentialRetry _er;
        private final IContentDownloads _dls;
        private final ContentFetcherIterator.Factory _factIter;

        @Inject
        public Factory(CoreScheduler sched, IContentDownloads dls, TransManager tm,
                       CoreExponentialRetry cer, ICollectorFilterDatabase cfdb,
                       ContentFetcherIterator.Factory factIter)
        {
            _sched = sched;
            _cfdb = cfdb;
            _tm = tm;
            _er = cer;
            _dls = dls;
            _factIter = factIter;
        }

        public ContentFetcher create_(SIndex sidx)
                throws SQLException
        {
            return new ContentFetcher(this, sidx);
        }
    }

    private final Factory _f;

    private ContentFetcher(Factory f, SIndex sidx)
            throws SQLException
    {
        _f = f;
        _sidx = sidx;
        _cfs = new CollectorFilters(f._cfdb, f._tm, sidx);
        _it = f._factIter.create_(sidx);
        resetBackoffInterval_();
    }

    /**
     * Add the specified bf to the device. N.B. the caller must guarantee that
     * the KML version of the components that are included in the filter has
     * been added to the db before this method is called. otherwise the method
     * would discard the filter without attempting downloading these objects
     */
    public void add_(final DID did, final @Nonnull BFOID filter, Trans t) throws SQLException
    {
        if (_cfs.addDBFilter_(did, filter, t)) {
            t.addListener_(new AbstractTransListener() {
                @Override
                public void committed_()
                {
                    l.debug("adding filter to {} triggers collector 4 {}", did, _sidx);
                    resetBackoffInterval_();
                    if (_it.started_()) {
                        _cfs.addCSFilter_(did, _it.cs_(), filter);
                    } else {
                        start_();
                    }
                }
            });
        }
    }

    public void online_(final DID did)
    {
        _f._er.retry("online", () -> {
            if (!_stopped && _cfs.loadDBFilter_(did)) {
                l.debug("{} online triggers collector 4 {}", did, _sidx);
                resetBackoffInterval_();
                if (_it.started_()) {
                    _cfs.setCSFilterFromDB_(did, _it.cs_());
                } else {
                    start_();
                }
            }

            return null;
        });
    }

    public void offline_(DID did)
    {
        // if no more device is left online, and the collection is on going, the next iteration of
        // the collector will find no filters are available, and therefore stop collecting.
        _cfs.unloadAllFilters_(did);
    }

    /**
     * start an iteration immediately. the caller must guarantee that _it.started_()
     * returns false.
     */
    private void start_()
    {
        checkState(!_it.started_());
        final int startSeq = ++_startSeq;

        _f._er.retry("start", () -> {
            // stop this retry thread if someone called start_() again
            if (_stopped || startSeq != _startSeq) return null;
            collect_();
            return null;
        });
    }

    private void restart_()
    {
        // add all db filters to cs to force the current iteration to run a full cycle
        // NB: we can safely fully reset the CS filter queue because the DB always contains the
        // canonical copy of the latest BF for each device and it is the union of all BF received
        // since the last cleanup (i.e. the last time collection was finalized)
        if (_it.started_()) {
            _cfs.deleteAllCSFilters_();
            _cfs.setAllCSFiltersFromDB_(_it.cs_());
        } else {
            start_();
        }
    }

    private void resetBackoffInterval_()
    {
        _backoffInterval = LibParam.EXP_RETRY_MIN_DEFAULT;
    }

    private void scheduleBackoff_()
    {
        l.debug("schedule backoff {} in {}", _backoffScheduled, _backoffInterval);

        if (_backoffScheduled) return;
        _backoffScheduled = true;

        _f._sched.schedule(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                l.debug("backoff start {}", _it);
                _backoffScheduled = false;
                restart_();
            }
        }, _backoffInterval);

        _backoffInterval = Math.min(_backoffInterval * 2, 10 * C.MIN);
    }

    /**
     * Wrap the collection loop in a transaction
     */
    private void collect_() throws SQLException
    {
        try (Trans t = _f._tm.begin_()) {
            collectLoop_(t);

            l.debug("collect {} ret. {}", _sidx, _it);
            attemptToStopAndFinalizeCollection_(t);

            t.commit_();
        }
    }

    /**
     * Iterate over the collector queue until collection can be finalized or a lack of Token
     * requires a break
     */
    private void collectLoop_(Trans t) throws SQLException
    {
        if (_it.started_()) {
            // in case of continuation we need to check again whether the component should be
            // skipped since its state might have changed since the last iteration
            OID oid = _it.current_();
            SOID soid = new SOID(_sidx, oid);
            l.debug("cont {}", soid);
            if (!collectOne_(oid)) {
                // wait for continuation
                return;
            }
        }

        while (rotate_(t)) {
            if (!collectOne_(_it.current_())) {
                // wait for continuation
                return;
            }
        }

        // this code is only reached if the iteration is over
        _it.reset_();
    }

    /**
     * Increment the collection iterator and discard BF as needed
     * @return whether there is an item to be collected
     */
    private boolean rotate_(Trans t) throws SQLException
    {
        OIDAndFetchIdx prevOccs = _it.currentNullable_();

        if (_it.next_(t)) {
            OIDAndFetchIdx occs = _it.current_();
            if (prevOccs == null) {
                // at this point the collection just started. it's noteworthy that there may be
                // ongoing downloads initiated by the last iteration.
                l.debug("start from {}", occs);
                _cfs.setAllCSFiltersFromDB_(new CollectorSeq(occs.idx));
            } else {
                checkState(prevOccs.idx < occs.idx);
                if (!_cfs.deleteCSFilters_(new CollectorSeq(prevOccs.idx + 1), new CollectorSeq(occs.idx))) {
                    l.debug("no filter left. stop");
                    return false;
                }
            }
            return true;

        } else if (prevOccs != null) {
            // end of queue reached: need to start over to cleanup all successfully collected item
            // and retry those that failed
            l.debug("reached end at {}", prevOccs);
            _it.reset_();
            if (_it.next_(t)) {
                OIDAndFetchIdx occs = _it.current_();
                l.debug("start over from {}", occs);
                // the two can be equal if there's only one component in the list
                checkState(prevOccs.idx >= occs.idx);
                // If the collector queue only contains one item we should make sure we do not
                // remove any freshly added filter before we collect that item
                // CollectorSeq.min(occs._cs, prevOccs._cs.minusOne())
                if (!_cfs.deleteCSFilters_(null, new CollectorSeq(occs.idx)) ||
                        !_cfs.deleteCSFilters_(new CollectorSeq(prevOccs.idx + 1), null)) {
                    l.debug("no filter left. stop");
                    return false;
                }
                return true;
            }
        }

        l.debug("empty list. stop");
        _cfs.deleteAllCSFilters_();
        return false;
    }

    /**
     * Stop and finalize collection if the following conditions are met:
     * 1) the collection iteration is done
     * 2) all the downloads initiated by the iteration have finished.
     *
     * Note that before the last download finishes, there may be one or more other
     * collection iterations have started and then stopped, or still on going, which is okay.
     */
    private void attemptToStopAndFinalizeCollection_(@Nullable Trans t)
    {
        checkState(_downloads >= 0, _downloads);

        if (!_it.started_() && _downloads == 0) {
            l.debug("stop clct on {}", _sidx);
            try {
                _cfs.cleanUpDBFilters_(t);
            } catch (SQLException e) {
                // filters may be failed to be removed from the db which is not
                // a big deal
                l.debug("stop clct 4 {}, ignored: {}", _sidx, e);
            }
        }
    }

    /**
     * Attempt to collect a component
     * @return false if collection failed due to lack of token, thus requiring a continuation
     */
    private boolean collectOne_(OID oid)
    {
        checkState(_it.started_());

        SOID soid = new SOID(_sidx, oid);
        final Set<DID> dids = _cfs.getDevicesHavingComponent_(oid);
        if (dids.isEmpty()) {
            l.debug("nodev {}", soid);
            return true;
        }

        l.debug("clct {} {}", soid, dids);

        boolean ok = _f._dls.downloadAsync_(soid, dids, new ContinuationTrigger(),
                new DownloadListener(dids));

        if (ok) {
            ++_downloads;
        } else {
            l.debug("request continuation");
        }

        return ok;
    }

    private class ContinuationTrigger implements ITokenReclamationListener
    {
        @Override
        public void tokenReclaimed_(final @Nonnull Runnable cascade)
        {
            l.debug("start continuation");
            _f._er.retry("continuation", () -> {
                if (_stopped) return null;
                checkState(_it.started_());
                collect_();
                cascade.run();
                return null;
            });
        }
    }

    private class DownloadListener implements IDownloadCompletionListener
    {
        private final Set<DID> _dids;

        DownloadListener(Set<DID> dids)
        {
            _dids = dids;
        }

        private void postDownloadCompletionTask_()
        {
            Util.verify(--_downloads >= 0);
            l.debug("dlend: {}", _downloads);
            attemptToStopAndFinalizeCollection_(null);
        }

        @Override
        public void onDownloadSuccess_(SOID soid, DID from)
        {
            postDownloadCompletionTask_();
        }

        @Override
        public void onPartialDownloadSuccess_(SOID soid, DID didFrom) { }

        @Override
        public void onGeneralError_(SOID soid, Exception e)
        {
            l.debug("cdl {}: {}", soid, BaseLogUtil.suppress(e,
                    ExAborted.class, ExNoAvailDevice.class, ExTimeout.class, ExStreamInvalid.class));

            // For general errors, we want to re-run this collector.
            // Indicate the BFs are dirty for all devices of interest so
            // that they are re-introduced into the next collector iteration.
            for (DID did : _dids) _cfs.setDirtyBit_(did);
            checkState(!_dids.isEmpty(), "%s %s", soid, e);
            scheduleBackoff_();
            postDownloadCompletionTask_();
        }

        @Override
        public void onPerDeviceErrors_(SOID soid, Map<DID, Exception> did2e)
        {
            // Gather the set of DIDs from which we received non-permanent errors
            // 1) First determine those *with* permanent errors
            Set<DID> didsWithPermanentErrors = Sets.newHashSetWithExpectedSize(did2e.size());
            for (Entry<DID, Exception> entry : did2e.entrySet()) {
                if (isPermanentError(soid, entry.getValue())) {
                    didsWithPermanentErrors.add(entry.getKey());
                }
            }
            // 2) Use set theory to complement that set correctly
            // N.B. because the set of DIDs in the device-specific errors map is not necessarily a
            // superset of {@code _dids}, we must take the union of the two sets, then remove any
            // DIDs that have permanent errors. We don't know whether these additional devices from
            // {@code _dids} failed due to a permanent or transient error.
            Set<DID> didsWithoutPermanentErrors =
                    Sets.difference(Sets.union(_dids, did2e.keySet()), didsWithPermanentErrors);

            // Rerun the collector for devices with non-permanent errors only:
            // 1) set the dirty bit for those devices
            // 2) schedule another collector run
            //
            // Safety Proof: Weihan raised a valid concern about when a collector filter is received
            // immediately after a permament error, from the same device, but I'll
            // quickly reason about why the Collector algorithm handles this safely. If a permanent
            // error is received from device A, then we do *not* set the dirty bit for its collector
            // filter (below), therefore when the Collector stops, device A's existing filter will
            // be deleted. Meanwhile, just after receipt of the permanent error, device A had an
            // update, so it sent a new bloom filter. Weihan's concern was that this newly-received
            // filter would get discarded prematurely because the dirty bit was not set. However,
            // adding a filter to an already-started collection (such as in this story line) also
            // adds that filter to the bottom of the collector sequence (see
            // {@code Collector.add_}). So when device A's new filter was received, it prevented the
            // collection from stopping until all objects were queried into that filter, and since
            // filters can only be deleted when collection *stops*, this avoids the problem of not
            // collecting objects using device A's new updated filter. QED (for this one case)
            if (!didsWithoutPermanentErrors.isEmpty()) {
                for (DID did : didsWithoutPermanentErrors) _cfs.setDirtyBit_(did);
                scheduleBackoff_();
            }

            // TODO: if all dids have perm errors we should remove the object from the collector q
            // NB: this is only possible if no KML were added during the dl, i.e. iff KML ver before
            // dl dominates KML ver after dl
            // this might get rid of the endless gcc loop caused by ghost KMLs without requiring
            // a KML cleanup (assuming all devices advertising the files can be contacted in the
            // same collector iteration...)

            l.debug("cdl {} didsWPerm {} didsWOPerm {}", soid,
                    didsWithPermanentErrors, didsWithoutPermanentErrors);

            postDownloadCompletionTask_();
        }

        @Override
        public String toString()
        {
            return "CLT("+ _dids +")";
        }
    }

    /**
     * An soid is considered to be in a permanent error if any of the following is true:
     *      - it threw an IExPermanentError
     *      - it has an unsatisfied dep on another component of the same soid for which a
     *      permanent error was thrown
     *
     * NB: the code is slightly obscured by the need to unwrap semantically wrapped exceptions
     * to look for a potential permanent error
     */
    // TODO: distinguish between "perm error" and "perm dep errror" to inhibit backoff
    private static boolean isPermanentError(SOID soid, Exception e)
    {
        if (e instanceof ExWrapped) {
            return isPermanentError(soid, ((ExWrapped)e).unwrap());
        }
        return e instanceof IExPermanentError;
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
    {
        ps.println(indent + "it: " + _it + " dls: " + _downloads +
                " backoff: " + _backoffScheduled);
    }

    @Override
    public String toString()
    {
        return "C["+ Joiner.on(' ').useForNull("null")
                .join(_sidx, _it, _downloads, _startSeq, _backoffScheduled) + "]";
    }

    public void deletePersistentData_(Trans t)
            throws SQLException
    {
        // N.B. it is safe to have downloads happening, and a collection loop running when deleting
        // persistent collector data for this store.
        // If a store is deleted (along w its persistent data) it is very difficult/impossible to
        // kill all existing downloads immediately.  So the downloads in other threads will realize
        // the store has been deleted, and the file/folder discarded.
        // If a collection was running, when that thread context switches back in, all of the
        // collector sequences will have been deleted, so the collector will soon wrap up and stop
        _cfs.deletePersistentData_(t);
    }
}

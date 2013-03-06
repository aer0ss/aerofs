package com.aerofs.daemon.core.collector;

import java.io.PrintStream;
import java.sql.SQLException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.Set;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.lib.id.SIndex;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import org.slf4j.Logger;

import com.aerofs.daemon.core.CoreExponentialRetry;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.collector.iterator.IIterator;
import com.aerofs.daemon.core.collector.iterator.IIteratorFactory;
import com.aerofs.daemon.core.collector.iterator.full.IteratorFactoryFullReplica;
import com.aerofs.daemon.core.collector.iterator.partial.IteratorFactoryPartialReplica;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.protocol.Downloads;
import com.aerofs.daemon.core.protocol.IDownloadCompletionListener;
import com.aerofs.daemon.core.net.To;
import com.aerofs.daemon.core.store.Store;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.ITokenReclamationListener;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.sched.ExponentialRetry;
import com.aerofs.lib.IDumpStatMisc;
import com.aerofs.daemon.lib.db.ICollectorFilterDatabase;
import com.aerofs.daemon.lib.db.ICollectorSequenceDatabase;
import com.aerofs.daemon.lib.db.ICollectorSequenceDatabase.OCIDAndCS;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.base.C;
import com.aerofs.lib.InOutArg;
import com.aerofs.lib.Param;
import com.aerofs.lib.Util;
import com.aerofs.lib.bf.BFOID;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.id.SOCID;
import com.google.inject.Inject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class Collector implements IDumpStatMisc
{
    private static final Logger l = Loggers.getLogger(Collector.class);

    private final SIndex _sidx;
    private final CollectorFilters _cfs;
    private final IIteratorFactory _iterFactory;
    private int _startSeq;
    private @Nullable OCIDAndCS _occs;    // null iff the collection is not started
    private int _downloads;     // # current downloads initiated by the collector
    private long _backoffInterval;
    private boolean _backoffScheduled;

    public static class Factory
    {
        private final CoreScheduler _sched;
        private final Downloads _dls;
        private final ICollectorSequenceDatabase _csdb;
        private final ICollectorFilterDatabase _cfdb;
        private final NativeVersionControl _nvc;
        private final DirectoryService _ds;
        private final TransManager _tm;
        private final To.Factory _factTo;
        private final ExponentialRetry _er;
        private final TokenManager _tokenManager;

        @Inject
        public Factory(CoreScheduler sched, Downloads dls, ICollectorSequenceDatabase csdb,
                NativeVersionControl nvc, DirectoryService ds, TransManager tm,
                To.Factory factTo, CoreExponentialRetry cer, ICollectorFilterDatabase cfdb,
                TokenManager tokenManager)
        {
            _sched = sched;
            _dls = dls;
            _csdb = csdb;
            _cfdb = cfdb;
            _nvc = nvc;
            _ds = ds;
            _tm = tm;
            _factTo = factTo;
            _er = cer;
            _tokenManager = tokenManager;
        }

        public Collector create_(Store s)
        {
            return new Collector(this, s);
        }
    }

    private final Factory _f;

    private Collector(Factory f, Store s)
    {
        _f = f;
        _sidx = s.sidx();
        // TODO use injection
        _cfs = new CollectorFilters(f._cfdb, f._tm, _sidx);
        _iterFactory = Cfg.isFullReplica() ?
                new IteratorFactoryFullReplica(f._csdb, f._nvc, f._ds, _sidx) :
                new IteratorFactoryPartialReplica(f._csdb, f._nvc, f._ds, s);
        resetBackoffInterval_();
    }

    /**
     * Add the specified bf to the device. N.B. the caller must guarantee that
     * the KML version of the components that are included in the filter has
     * been added to the db before this method is called. otherwise the method
     * would discard the filter without attempting downloading these objects
     */
    public void add_(DID did, @Nonnull BFOID filter, Trans t) throws SQLException
    {
        if (_cfs.addDBFilter_(did, filter, t)) {
            l.debug("adding filter to " + did + " triggers collector 4 " + _sidx);
            resetBackoffInterval_();
            if (started_()) _cfs.addCSFilter_(did, _occs._cs, filter);
            else start_(t);
        }
    }

    public void online_(final DID did)
    {
        _f._er.retry("online", new Callable<Void>()
        {
            @Override
            public Void call()
                    throws Exception
            {
                if (_cfs.loadDBFilter_(did)) {
                    l.debug(did + " online triggers collector 4 " + _sidx);
                    resetBackoffInterval_();
                    if (started_()) _cfs.setCSFilterFromDB_(did, _occs._cs);
                    else start_(null);
                }

                return null;
            }
        });
    }

    public void offline_(DID did)
    {
        // if no more device is left online, and the collection is on going, the next iteration of
        // the collector will find no filters are available, and therefore stop collecting.
        _cfs.unloadAllFilters_(did);
    }

    private boolean started_()
    {
        return _occs != null;
    }

    /**
     * start an iteration immediately. the caller must guarantee that started_()
     * returns false.
     */
    private void start_(@Nullable final Trans t)
    {
        assert !started_();
        final int startSeq = ++_startSeq;

        _f._er.retry("start", new Callable<Void>()
        {
            boolean _first = true;

            @Override
            public Void call() throws Exception
            {
                /**
                 * The Trans object is only valid on the first call (ExpRetry will schedule any sub-
                 * sequent retries as separate self-handling events) so we need to make sure we
                 * reset it before we call collect_ as it may throw...
                 *
                 * NB: using ExpRetry within a transaction is Bad(tm) but I'm not familiar enough
                 * with the code to refactor it now...
                 *
                 * TODO(hugues): schedule collection in a trans listener instead?
                 */
                boolean isFirst = _first;
                _first = false;

                // stop this retry thread if someone called start_() again
                if (startSeq != _startSeq) return null;

                assert !started_() : isFirst + " " + Collector.this;
                collect_(isFirst ? t : null);
                return null;
            }
        });
    }

    public void restart_()
    {
        // add all db filters to cs to force the current iteration to run a full cycle
        // NB: we can safely fully reset the CS filter queue because the DB always contains the
        // canonical copy of the latest BF for each device and it is the union of all BF received
        // since the last cleanup (i.e. the last time collection was finalized)
        if (started_()) {
            _cfs.deleteAllCSFilters_();
            _cfs.setAllCSFiltersFromDB_(_occs._cs);
        } else {
            start_(null);
        }
    }

    private void resetBackoffInterval_()
    {
        _backoffInterval = Param.EXP_RETRY_MIN_DEFAULT;
    }

    private void scheduleBackoff_()
    {
        l.debug("schedule backoff " + _backoffScheduled + " in " + _backoffInterval);

        if (_backoffScheduled) return;
        _backoffScheduled = true;

        _f._sched.schedule(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                l.debug("backoff start " + _occs);
                _backoffScheduled = false;
                restart_();
            }
        }, _backoffInterval);

        _backoffInterval = Math.min(_backoffInterval * 2, 10 * C.MIN);
    }

    /**
     * this method can only be called by start() or collectOne()
     */
    private void collect_(@Nullable Trans t)
            throws SQLException
    {
        // depending on _occs, we may either perform a iterating continuation or
        // start a new iteration
        IIterator iter = _iterFactory.newIterator_(_occs == null ? null : _occs._cs);
        InOutArg<IIterator> ioaIter = new InOutArg<IIterator>(iter);
        try {
            collectLoop_(t, ioaIter);
        } finally {
            ioaIter.get().close_();
        }

        l.debug("collect " + _sidx + " returns. occs = " + _occs);
        attemptToStopAndFinalizeCollection_(t);
    }

    private void collectLoop_(Trans t, InOutArg<IIterator> ioaIter) throws SQLException
    {
        boolean hasEntry = false;
        boolean firstLoop = true;

        while (true) {
            if (_occs != null) {
                // _occs != null iff 1) we're in a continuation and just entered this while loop
                // (firstLoop == true), or 2) the iter.next() below has picked up a new element
                // (firstLoop == false).
                hasEntry = true;

                boolean shouldCollect;
                if (firstLoop) {
                    // if the occs is carried over from the last iteration due to continuation
                    // (i.e. case 1 above), we need to check again whether the component should be
                    // skipped since its state might have been changed after the last iteration
                    // and before this one.
                    SOCID socid = new SOCID(_sidx, _occs._ocid);
                    shouldCollect = !Common.shouldSkip_(_f._nvc, _f._ds, socid);
                } else {
                    shouldCollect = true;
                }

                if (shouldCollect && !collectOne_(_occs)) {
                    // wait for continuation
                    break;
                }
            }

            OCIDAndCS occs = ioaIter.get().next_(t);
            if (occs != null) {
                if (_occs == null) {
                    // at this point the collection just started. it's noteworthy that there may be
                    // ongoing downloads initiated by the last iteration.
                    _cfs.setAllCSFiltersFromDB_(occs._cs);
                    _occs = occs;
                } else {
                    assert _occs._cs.compareTo(occs._cs) < 0;
                    if (!_cfs.deleteCSFilters_(_occs._cs.plusOne(), occs._cs)) {
                        l.debug("no filter left. stop");
                        _occs = null;
                        break;
                    } else {
                        _occs = occs;
                    }
                }

            } else if (hasEntry) {
                ioaIter.get().close_();
                l.debug("start over");
                ioaIter.set(_iterFactory.newIterator_(null));
                assert _occs != null;
                occs = ioaIter.get().next_(t);
                if (occs != null) {
                    // the two can be equal if there's only one component in
                    // the list
                    assert _occs._cs.compareTo(occs._cs) >= 0;
                    if (!_cfs.deleteCSFilters_(null, occs._cs) ||
                            !_cfs.deleteCSFilters_(_occs._cs.plusOne(), null)) {
                        l.debug("no filter left. stop");
                        _occs = null;
                        break;
                    } else {
                        _occs = occs;
                    }
                } else {
                    l.debug("empty list. stop");
                    _cfs.deleteAllCSFilters_();
                    _occs = null;
                    break;
                }

            } else {
                // nothing has been collected. that means two things:
                // 1) it's not a continuation (otherwise _occs != null
                // when the method is called), and 2) therefore it's a
                // new iteration, and it returns nothing.
                // in this case, we stop
                l.debug("empty list. stop");
                _cfs.deleteAllCSFilters_();
                _occs = null;
                break;
            }

            firstLoop = false;
        }
    }

    /**
     * Stop and finalize collection if the following conditions are met:
     * 1) the collection iteration is done, and 2) all the downloads initiated by the iteration have
     * finished.
     * Note that before the last download finishes, there may be one or more other
     * collection iterations have started and then stopped, or still on going, which is okay.
     */
    private void attemptToStopAndFinalizeCollection_(@Nullable Trans t)
    {
        assert _downloads >= 0;

        if (!started_() && _downloads == 0) {
            l.debug("stop clct on " + _sidx);
            try {
                _cfs.cleanUpDBFilters_(t);
            } catch (SQLException e) {
                // filters may be failed to be removed from the db which is not
                // a big deal
                l.debug("stop clct 4 " + _sidx + ", ignored: " + Util.e(e));
            }
        }
    }

    /**
     * @return false to stop the iteration and wait for continuation
     */
    private boolean collectOne_(OCIDAndCS occs)
    {
        assert started_();

        SOCID socid = new SOCID(_sidx, occs._ocid.oid(), occs._ocid.cid());
        final Set<DID> dids = _cfs.getDevicesHavingComponent_(occs._ocid);
        if (dids.isEmpty()) return true;

        l.debug("clct " + _sidx + occs + " " + dids);

        final Token tk;
        if (_f._dls.isOngoing_(socid)) {
            // no token is needed for ongoing downloads
            tk = null;
        } else {
            Cat cat = _f._dls.getCat();
            tk = _f._tokenManager.acquire_(cat, "collect " + _sidx + occs._ocid);
            if (tk == null) {
                l.debug("request continuation");
                _f._tokenManager.addTokenReclamationListener_(cat, new ITokenReclamationListener()
                {
                    @Override
                    public void tokenReclaimed_(Cat cat)
                    {
                        l.debug("start continuation");
                        _f._er.retry("continuation", new Callable<Void>()
                        {
                            @Override
                            public Void call() throws Exception
                            {
                                assert started_();
                                collect_(null);
                                return null;
                            }
                        });
                    }
                });
                return false;
            }
        }

        // to guarantee tk is reclaimed properly, no code should be added here

        boolean enqueued = false;
        try {
            // download the component even if it's already being downloaded, so
            // that the new destination devices can be added if they are not there
            Util.verify(_f._dls
                    .downloadAsync_(socid, _f._factTo.create_(dids),
                            new CollectorDownloadListener(dids, tk), tk));

            _downloads++;
            enqueued = true;
            return true;
        } finally {
            if (tk != null && !enqueued) tk.reclaim_();
        }
    }

    private class CollectorDownloadListener implements IDownloadCompletionListener
    {
        private final Set<DID> _dids;
        private final @Nullable Token _tk;

        CollectorDownloadListener(Set<DID> dids, @Nullable Token tk)
        {
            _dids = dids;
            _tk = tk;
        }

        private void postDownloadCompletionTask()
        {
            Util.verify(_downloads-- >= 0);
            attemptToStopAndFinalizeCollection_(null);
        }

        @Override
        public void okay_(SOCID socid, DID from)
        {
            if (_tk != null) _tk.reclaim_();
            postDownloadCompletionTask();
        }

        @Override
        public void onGeneralError_(SOCID socid, Exception e)
        {
            l.debug("cdl " + socid + ": " + Util.e(e));
            if (_tk != null) _tk.reclaim_();

            // For general errors, we want to re-run this collector.
            // Indicate the BFs are dirty for all devices of interest so
            // that they are re-introduced into the next collector iteration.
            for (DID did : _dids) _cfs.setDirtyBit_(did);
            assert !_dids.isEmpty() : socid + " " + Util.e(e);
            scheduleBackoff_();
            postDownloadCompletionTask();
        }

        @Override
        public void onPerDeviceErrors_(SOCID socid, Map<DID, Exception> did2e)
        {
            if (_tk != null) _tk.reclaim_();

            // Gather the set of DIDs from which we received non-permanent errors
            // 1) First determine those *with* permanent errors
            Set<DID> didsWithPermanentErrors = Sets.newHashSetWithExpectedSize(did2e.size());
            for (Entry<DID, Exception> entry : did2e.entrySet()) {
                if (isPermanentError(entry.getValue())) didsWithPermanentErrors.add(entry.getKey());
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

            l.debug("cdl "+ socid + " didsWPerm " + didsWithPermanentErrors
                    + " didsWOPerm " + didsWithoutPermanentErrors);

            postDownloadCompletionTask();
        }

    }

    /**
     * @param e the exception received from the remote peer when trying to download an object
     * @return whether the remote exception is a "permanent" error.
     * See {@code AbstractExPermanentError} for details
     */
    private static boolean isPermanentError(Exception e)
    {
        return e instanceof AbstractExPermanentError;
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
    {
        ps.println(indent + "occs: " + _occs + " dls: " + _downloads +
                " backoff: " + _backoffScheduled);
    }

    @Override
    public String toString()
    {
        return "C["+ Joiner.on(' ').useForNull("null")
                .join(_sidx, _occs, _downloads, _startSeq, _backoffScheduled) + "]";
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

        // TODO (MJ) this is the only place Collector touches the csdb. Thus it seems hacky that the
        // Collector is responsible for "owning" the CSDB.
        _f._csdb.deleteCSsForStore_(_sidx, t);
        _cfs.deletePersistentData_(t);
    }
}

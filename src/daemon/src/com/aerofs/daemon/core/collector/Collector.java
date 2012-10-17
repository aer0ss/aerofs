package com.aerofs.daemon.core.collector;

import java.io.PrintStream;
import java.sql.SQLException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.Set;

import com.aerofs.lib.ex.collector.AbstractExPermanentError;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import org.apache.log4j.Logger;

import com.aerofs.daemon.core.CoreExponentialRetry;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.collector.iterator.IIterator;
import com.aerofs.daemon.core.collector.iterator.IIteratorFactory;
import com.aerofs.daemon.core.collector.iterator.full.IteratorFactoryFullReplica;
import com.aerofs.daemon.core.collector.iterator.partial.IteratorFactoryPartialReplica;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.net.Downloads;
import com.aerofs.daemon.core.net.IDownloadCompletionListener;
import com.aerofs.daemon.core.net.To;
import com.aerofs.daemon.core.store.Store;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.ITokenReclamationListener;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.event.lib.AbstractEBSelfHandling;
import com.aerofs.daemon.lib.ExponentialRetry;
import com.aerofs.daemon.lib.IDumpStatMisc;
import com.aerofs.daemon.lib.db.ICollectorFilterDatabase;
import com.aerofs.daemon.lib.db.ICollectorSequenceDatabase;
import com.aerofs.daemon.lib.db.ICollectorSequenceDatabase.OCIDAndCS;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.C;
import com.aerofs.lib.InOutArg;
import com.aerofs.lib.Param;
import com.aerofs.lib.Util;
import com.aerofs.lib.bf.BFOID;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SOCID;
import com.google.inject.Inject;

import javax.annotation.Nullable;

public class Collector implements IDumpStatMisc
{
    private static final Logger l = Util.l(Collector.class);

    private final Store _s;
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
        _s = s;
        // TODO use injection
        _cfs = new CollectorFilters(f._cfdb, f._tm, s.sidx());
        _iterFactory = Cfg.isFullReplica() ?
                new IteratorFactoryFullReplica(f._csdb, f._nvc, f._ds, s.sidx()) :
                new IteratorFactoryPartialReplica(f._csdb, f._nvc, f._ds, s);
        resetBackoffInterval_();
    }

    /**
     * Add the specified bf to the device. N.B. the caller must guarantee that
     * the KML version of the components that are included in the filter has
     * been added to the db before this method is called. otherwise the method
     * would discard the filter without attempting downloading these objects
     */
    public void add_(DID did, BFOID filter, Trans t) throws SQLException
    {
        if (_cfs.addDBFilter_(did, filter, t)) {
            l.info("adding filter to " + did + " triggers collector 4 " + _s.sidx());
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
                    l.info(did + " online triggers collector 4 " + _s.sidx());
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
                // stop this retry thread if someone called start_() again
                if (startSeq != _startSeq) return null;

                assert !started_() : Joiner.on(' ').join(_occs, _first, _startSeq);
                collect_(_first ? t : null);
                return null;
            }
        });
    }

    public void restart_()
    {
        // add all db filters to cs to force the current iteration to
        // run a full cycle
        if (started_()) _cfs.addAllCSFiltersFromDB_(_occs._cs);
        else start_(null);
    }

    private void resetBackoffInterval_()
    {
        _backoffInterval = Param.EXP_RETRY_MIN_DEFAULT;
    }

    private void scheduleBackoff_()
    {
        l.info("schedule backoff " + _backoffScheduled + " in " + _backoffInterval);

        if (_backoffScheduled) return;
        _backoffScheduled = true;

        _f._sched.schedule(new AbstractEBSelfHandling()
        {
            @Override
            public void handle_()
            {
                l.info("backoff start " + _occs);
                _backoffScheduled = false;
                restart_();
            }
        }, _backoffInterval);

        _backoffInterval = Math.min(_backoffInterval * 2, 10 * C.MIN);
    }

    /**
     * this method can only be called by start() or collectOne()
     */
    private void collect_(@Nullable Trans t) throws Exception
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

        l.info("collect " + _s.sidx() + " returns. occs = " + _occs);
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
                    SOCID socid = new SOCID(_s.sidx(), _occs._ocid);
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
                        l.info("no filter left. stop");
                        _occs = null;
                        break;
                    } else {
                        _occs = occs;
                    }
                }

            } else if (hasEntry) {
                ioaIter.get().close_();
                l.info("start over");
                ioaIter.set(_iterFactory.newIterator_(null));
                assert _occs != null;
                occs = ioaIter.get().next_(t);
                if (occs != null) {
                    // the two can be equal if there's only one component in
                    // the list
                    assert _occs._cs.compareTo(occs._cs) >= 0;
                    if (!_cfs.deleteCSFilters_(null, occs._cs) ||
                            !_cfs.deleteCSFilters_(_occs._cs.plusOne(), null)) {
                        l.info("no filter left. stop");
                        _occs = null;
                        break;
                    } else {
                        _occs = occs;
                    }

                } else {
                    l.info("empty list. stop");
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
                l.info("empty list. stop");
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
            l.info("stop clct on " + _s.sidx());
            try {
                _cfs.cleanUpDBFilters_(t);
            } catch (SQLException e) {
                // filters may be failed to be removed from the db which is not
                // a big deal
                l.info("stop clct 4 " + _s.sidx() + ", ignored: " + Util.e(e));
            }
        }
    }

    /**
     * @return false to stop the iteration and wait for continuation
     */
    private boolean collectOne_(OCIDAndCS occs)
    {
        assert started_();

        SOCID socid = new SOCID(_s.sidx(), occs._ocid.oid(), occs._ocid.cid());
        final Set<DID> dids = _cfs.getDevicesHavingComponent_(occs._ocid);
        if (dids.isEmpty()) return true;

        l.info("clct " + _s.sidx() + occs + " " + dids);

        final Token tk;
        if (_f._dls.isOngoing_(socid)) {
            // no token is needed for ongoing downloads
            tk = null;
        } else {
            Cat cat = _f._dls.getCat();
            tk = _f._tokenManager.acquire_(cat, "collect " + _s.sidx() + occs._ocid);
            if (tk == null) {
                l.info("request continuation");
                _f._tokenManager.addTokenReclamationListener_(cat, new ITokenReclamationListener()
                {
                    @Override
                    public void tokenReclaimed_(Cat cat)
                    {
                        l.info("start continuation");
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
            _s.downloaded_(socid);
            postDownloadCompletionTask();
        }

        @Override
        public void onGeneralError_(SOCID socid, Exception e)
        {
            l.info("cdl " + socid + ": " + Util.e(e));
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
}

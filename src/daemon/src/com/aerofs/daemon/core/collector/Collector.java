package com.aerofs.daemon.core.collector;

import java.io.PrintStream;
import java.sql.SQLException;
import java.util.concurrent.Callable;
import java.util.Set;

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
    private OCIDAndCS _occs;    // null iff the collection is not started
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
            l.info("adding filter to " + did + " triggers collector 4 " +
                    _s.sidx());
            resetBackoffInterval_();
            if (started_()) _cfs.addCSFilter_(did, _occs._cs, filter);
            else start_(t);
        }
    }

    /**
     * @param t may be null, but only if there is not pending transaction
     */
    public void online_(final DID did, Trans t)
    {
        _f._er.retry("online", new Callable<Void>()
        {
            @Override
            public Void call() throws Exception
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
        // if no more device is left online, and the collection is on going,
        // the next iteration of the collector will find no filters are
        // available, and therefore stop collecting.
        _cfs.unloadAllFilters_(did);
    }

    private boolean started_()
    {
        return _occs != null;
    }

    /**
     * start an iteration immediately. the caller must guarantee that started_()
     * returns false.
     *
     * @param t may be null
     */
    private void start_(final Trans t)
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

                assert !started_();
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
     *
     * @param t may be null
     */
    private void collect_(Trans t) throws Exception
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
        testStop_(t);
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
     * TODO why is this called testStop?
     *
     * finalized the collection if 1) the collection iteration is done, and 2)
     * all the downloads initiated by the iteration have finished. Note that
     * before the last download finishes, there may be one or more other
     * collection iterations have started and then stopped, or still on going,
     * which is okay.
     */
    private void testStop_(@Nullable Trans t)
    {
        assert _downloads >= 0;

        if (!started_() && _downloads == 0) {
            l.info("testStop " + _s.sidx() + " does stop");
            try {
                _cfs.cleanUpDBFilters_(t);
            } catch (Exception e) {
                // filters may be failed to be removed from the db which is not
                // a big deal
                l.info("testStop 4 " + _s.sidx() + ", ignored: " + Util.e(e));
            }
        }
    }

    /**
     * @return false to stop the iteration and wait for continuation
     */
    private boolean collectOne_(OCIDAndCS occs)
    {
        assert started_();

        SOCID k = new SOCID(_s.sidx(), occs._ocid.oid(), occs._ocid.cid());
        final Set<DID> dids = _cfs.test_(occs._ocid);
        if (dids.isEmpty()) return true;

        l.warn("clct " + _s.sidx() + occs + " " + dids);

        final Token tk;
        if (_f._dls.isOngoing_(k)) {
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
            Util.verify(_f._dls.downloadAsync_(k, _f._factTo.create_(dids),
                    new IDownloadCompletionListener()
                    {
                        @Override
                        public void okay_(SOCID socid, DID from)
                        {
                            if (tk != null) tk.reclaim_();
                            Util.verify(_downloads-- >= 0);
                            testStop_(null);
                            _s.downloaded_(socid);
                        }

                        @Override
                        public void error_(SOCID socid, Exception e)
                        {
                            l.info("cdl " + socid + ": " + Util.e(e));
                            if (tk != null) tk.reclaim_();
                            // TODO handle permanent errors
                            for (DID did : dids) _cfs.setDirtyBit_(did);
                            Util.verify(_downloads-- >= 0);
                            testStop_(null);
                            scheduleBackoff_();
                        }
                    }, tk));

            _downloads++;
            enqueued = true;
            return true;
        } finally {
            if (tk != null && !enqueued) tk.reclaim_();
        }
    }

    @Override
    public void dumpStatMisc(String indent, String indentUnit, PrintStream ps)
    {
        ps.println(indent + "occs: " + _occs + " dls: " + _downloads +
                " backoff: " + _backoffScheduled);
    }
}

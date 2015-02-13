/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.polaris.fetch;

import com.aerofs.base.Loggers;
import com.aerofs.ids.DID;
import com.aerofs.ids.OID;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.collector.IExPermanentError;
import com.aerofs.daemon.core.ex.ExWrapped;
import com.aerofs.daemon.core.polaris.db.ContentFetchQueueDatabase;
import com.aerofs.daemon.core.polaris.db.RemoteContentDatabase;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.core.transfers.download.Downloads;
import com.aerofs.daemon.core.transfers.download.IDownloadCompletionListener;
import com.aerofs.daemon.lib.db.AbstractTransListener;
import com.aerofs.daemon.lib.db.trans.Trans;
import com.aerofs.daemon.lib.db.trans.TransLocal;
import com.aerofs.daemon.lib.db.trans.TransManager;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.sched.ExponentialRetry;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;


/**
 * Polaris equivalent of the Collector
 *
 * FIXME: much refactor and cleanup to come
 * need to get this thing off the ground as fast as possible for integration testing
 * corner cases, scalability and cleanliness all take a back seat for now
 *
 * TODO: bloom filters
 */
public class ContentFetcher
{
    private final static Logger l = Loggers.getLogger(ContentFetcher.class);

    private final SIndex _sidx;
    private final Factory _f;
    private final Set<DID> _devices = Sets.newHashSet();

    private boolean _scheduled;
    private int _ongoingDownloads;
    private int _totalDownloads;
    private int _permanentFailures;

    private ContentFetcherIterator _it;

    public static class Factory
    {
        private final CoreScheduler _sched;
        private final TransManager _tm;
        private final TokenManager _tokenManager;
        private final RemoteContentDatabase _rcdb;
        private final ContentFetchQueueDatabase _cfqdb;
        private final Downloads _dls;
        private final ExponentialRetry _er;
        private final ContentFetcherIterator.Factory _factCFI;

        @Inject
        public Factory(CoreScheduler sched, TransManager tm, TokenManager tokenManager,
                RemoteContentDatabase rcdb, ContentFetchQueueDatabase cfqdb, Downloads dls,
                ContentFetcherIterator.Factory factCFI)
        {
            _sched = sched;
            _tm = tm;
            _tokenManager = tokenManager;
            _rcdb = rcdb;
            _cfqdb = cfqdb;
            _dls = dls;
            _factCFI = factCFI;
            _er = new ExponentialRetry(sched);
        }

        public ContentFetcher create_(SIndex sidx)
        {
            return new ContentFetcher(sidx, this);
        }
    }

    private ContentFetcher(SIndex sidx, Factory f)
    {
        _sidx = sidx;
        _f = f;
        _it = _f._factCFI.create_(_sidx);
    }

    public void online_(DID did)
    {
        _devices.add(did);
        if (_devices.size() == 1) {
            start_();
        }
    }

    public void offline_(DID did)
    {
        _devices.remove(did);
    }

    private final TransLocal<Boolean> _tlSched = new TransLocal<Boolean>() {
        @Override
        protected Boolean initialValue(Trans t)
        {
            t.addListener_(new AbstractTransListener() {
                @Override
                public void committed_()
                {
                    if (_devices.size() > 0) start_();
                }
            });
            return null;
        }
    };

    public void schedule_(OID oid, Trans t) throws SQLException
    {
        _f._cfqdb.insert_(_sidx, oid, t);
        _tlSched.get(t);
    }

    private void start_()
    {
        if (_it.started_() || _ongoingDownloads > 0 || _scheduled) return;

        l.info("{} start content fetch", _sidx);
        _totalDownloads = 0;
        _permanentFailures = 0;
        schedule();
    }

    private void schedule()
    {
        _scheduled = true;
        _f._sched.schedule(new AbstractEBSelfHandling() {
            @Override
            public void handle_()
            {
                _scheduled = false;
                _f._er.retry("", () -> { fetch_(); return null; });
            }
        }, 0);
    }

    private void fetch_() throws SQLException
    {
        boolean retry;
        try (Trans t = _f._tm.begin_()) {
            retry = fetchLoop_(t);
            t.commit_();
        }
        if (retry) {
            l.debug("{} resched {}", _sidx, _it);
            schedule();
        }
    }

    private boolean fetchLoop_(Trans t) throws SQLException
    {
        l.info("{} content fetch from {}", _sidx, _it);

        if (_it.started_()) {
            l.debug("{} continuation {}", _sidx, _it);
            if (!fetchOne_(_it.current_())) {
                l.debug("{} wait for continuation", _sidx);
                return false;
            }
        }

        int n = 0;

        while (_it.next_(t)) {
            // break loop in chunks to avoid infinite loop
            // if the same object keeps failing with "transient" errors
            if (++n > 100) {
                // reschedule
                return true;
            }
            OID oid = _it.current_();
            if (!fetchOne_(oid)) {
                l.debug("{} wait for continuation {}", _sidx, _it);
                return false;
            }
        }

        l.info("{} end content fetch", _sidx);
        return false;
    }

    // TODO: bloom filters?

    private boolean fetchOne_(OID oid) throws SQLException
    {
        if (_devices.isEmpty()) {
            l.debug("{} nodev {}", _sidx, oid);
            return true;
        }
        l.debug("{} fetch {} from {}", _sidx, oid, _devices);

        boolean ok =  _f._dls.downloadAsync_(new SOCID(_sidx, oid, CID.CONTENT), _devices, cascade -> {
            _f._er.retry("continuation", () -> {
                l.debug("{} continuation", _sidx);
                fetch_();
                cascade.run();
                return null;
            });
        }, new DownloadListener(oid, _devices));
        if (ok) ++_ongoingDownloads;
        return ok;
    }

    private void tryFinalize_()
    {
        if (_it.started_() || _ongoingDownloads > 0) return;

        l.debug("{} reached end at {} {} {}", _sidx, _it, _permanentFailures, _totalDownloads);
        _it.reset_();
        if (_permanentFailures < _totalDownloads) {
            l.debug("{} new round", _sidx);
            _permanentFailures = 0;
            _totalDownloads = 0;
            schedule();
        }
    }

    private class DownloadListener implements IDownloadCompletionListener
    {
        private final OID _oid;
        private final Set<DID> _dids;

        DownloadListener(OID oid, Set<DID> dids)
        {
            _oid = oid;
            _dids = dids;
        }

        private void postDownloadCompletionTask_()
        {
            --_ongoingDownloads;
            ++_totalDownloads;
            l.debug("dlend: {} {} {}", _oid , _totalDownloads, _ongoingDownloads);
            tryFinalize_();
        }

        @Override
        public void onDownloadSuccess_(SOCID socid, DID from)
        {
            postDownloadCompletionTask_();
        }

        @Override
        public void onPartialDownloadSuccess_(SOCID socid, DID didFrom) { }

        @Override
        public void onGeneralError_(SOCID socid, Exception e)
        {
            l.debug("cdl {}", socid, e);
            postDownloadCompletionTask_();
        }

        @Override
        public void onPerDeviceErrors_(SOCID socid, Map<DID, Exception> did2e)
        {
            l.debug("cdl {}", socid);
            boolean allPerm = !did2e.isEmpty();
            for (Entry<DID, Exception> e : did2e.entrySet()) {
                allPerm &= isPermanentError(e.getValue());
            }
            if (allPerm) ++_permanentFailures;
            postDownloadCompletionTask_();
        }

        @Override
        public String toString()
        {
            return "CLT("+ _dids +")";
        }
    }

    private static boolean isPermanentError(Exception e)
    {
        return e instanceof ExWrapped
                ? isPermanentError(((ExWrapped)e).unwrap())
                : e instanceof IExPermanentError;
    }
}

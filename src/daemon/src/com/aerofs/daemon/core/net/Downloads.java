package com.aerofs.daemon.core.net;

import java.sql.SQLException;
import java.util.Map;

import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.event.lib.AbstractEBSelfHandling;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SOCID;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.apache.log4j.Logger;

import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.event.IEvent;
import com.aerofs.lib.Util;
import com.aerofs.lib.ex.ExAborted;
import com.aerofs.lib.ex.ExNoResource;
import com.aerofs.lib.id.DID;
import javax.annotation.Nullable;

// downloads are preemptive. That is, downloads may interrupt others with lower
// priorities. the download thread being interrupted will throw ExAborted with
// cause ExNoResource.

public class Downloads {
    private static final Logger l = Util.l(Downloads.class);

    private class SyncDownloadImpl implements IDownloadCompletionListener {

        private TCB _tcb;
        private DID _from;

        SyncDownloadImpl(SOCID socid, To to, Token tk, SOCID dependent)
                throws Exception
        {
            Token tkWait = _tc.acquireThrows_(Cat.UNLIMITED, "syncDL");
            try {
                downloadAsyncThrows_(socid, to, this, tk);
                _syncDownloadDeadlockDetector.addEdge_(dependent, socid);
                _tcb = TC.tcb();
                tkWait.pause_("syncDL " + socid);
            } catch (ExAborted e) {
                throw (Exception) e.getCause();
            } finally {
                _syncDownloadDeadlockDetector.removeEdge_(dependent, socid);
                tkWait.reclaim_();
                _tcb = null;
            }

            assert _from != null;
        }

        @Override
        public void error_(SOCID socid, Exception e)
        {
            if (_tcb != null) _tcb.abort_(e);
        }

        @Override
        public void okay_(SOCID socid, DID from)
        {
            _from = from;
            if (_tcb != null) _tcb.resume_();
        }
    }

    private TC _tc;
    private CoreScheduler _sched;
    private CoreQueue _q;
    private DownloadState _dlstate;
    private Download.Factory _factDownload;
    private To.Factory _factTo;
    private DirectoryService _ds;

    private final Map<SOCID, Download> _ongoing = Maps.newTreeMap();
    private final DownloadDependenciesGraph<SOCID> _syncDownloadDeadlockDetector
            = new DownloadDependenciesGraph<SOCID>();

    @Inject
    public void inject_(CoreQueue q, DownloadState dlstate, TC tc,
            CoreScheduler sched, Download.Factory factDownload,
            To.Factory factTo, DirectoryService ds)
    {
        _q = q;
        _dlstate = dlstate;
        _tc = tc;
        _sched = sched;
        _factDownload = factDownload;
        _factTo = factTo;
        _ds = ds;
    }

    public boolean isOngoing_(SOCID socid)
    {
        return _ongoing.containsKey(socid);
    }

    /**
     * @param dependent the branch that depends on the downloading of k. this is
     * to detect cyclic downloadSync calls (deadlocks). may be null
     */
    public DID downloadSync_(SOCID socid, To to, @Nullable Token tk, SOCID dependent)
        throws Exception
    {
        SyncDownloadImpl sdi = new SyncDownloadImpl(socid, to, tk, dependent);
        return sdi._from;
    }

    public Download downloadAsyncThrows_(SOCID socid, To to, IDownloadCompletionListener listener,
            Token tk)
            throws ExNoResource
    {
        Download dl = downloadAsync_(socid, to, listener, tk);
        if (dl == null) throw new ExNoResource("cat is full");
        return dl;
    }

    public Cat getCat()
    {
        return Cat.CLIENT;
    }

    /**
     * Ongoing downloads may preempt only if the new download has a strictly
     * higher priority.
     *
     * @param tk set to null to force to acquire a new token if the download doesn't exist
     * (i.e. if isOngoing() returns false)
     * @return null if tk == null and the Cat is full; non-null if the download is successfully
     * enqueued
     *
     * TODO merge tk with the existing one to adjust priorities
     */
    public @Nullable Download downloadAsync_(final SOCID socid, @Nullable To to,
            @Nullable IDownloadCompletionListener listener, @Nullable final Token tk)
    {
        try {
            // assert that the object is not expelled if the branch being downloaded is not metadata
            OA oa;
            assert socid.cid().equals(CID.META) ||
                    (oa = _ds.getOANullable_(socid.soid())) == null ||
                    !oa.isExpelled() : socid;
        } catch (SQLException e) { }

        // make a copy so that the caller may reuse the src afterwards.
        to = to == null ? _factTo.create_(socid.sidx()) : _factTo.create_(to);

        Download dlExisting = _ongoing.get(socid);
        if (dlExisting != null) {
            dlExisting.include_(to, listener);
            return dlExisting;

        } else {
            final Token tk2 = tk == null ? _tc.acquire_(getCat(), "dl " + socid) : tk;
            if (tk2 == null) {
                l.info("cat full 4 " + socid);
                return null;
            }

            l.info(socid);

            final Download dl = _factDownload.create_(socid, to, listener, tk2);

            IEvent ev = new AbstractEBSelfHandling() {
                    @Override
                    public void handle_()
                    {
                        try {
                            dl.do_();
                        } finally {
                            if (tk == null) tk2.reclaim_();
                            Util.verify(_ongoing.remove(socid));
                        }
                    }
                };

            // try enqueue first. schedule it if the queue is full
            if (!_q.enqueue_(ev, _tc.prio())) _sched.schedule(ev, 0);

            Util.verify(_ongoing.put(socid, dl) == null);

            _dlstate.enqueued_(socid);
            return dl;
        }
    }
}

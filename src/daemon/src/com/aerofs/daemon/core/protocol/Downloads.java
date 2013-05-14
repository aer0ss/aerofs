/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.protocol;

import java.sql.SQLException;
import java.util.Map;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.download.DownloadState;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.net.To;
import com.aerofs.daemon.core.protocol.dependence.DependencyEdge;
import com.aerofs.daemon.core.protocol.dependence.DownloadDependenciesGraph;
import com.aerofs.daemon.core.protocol.dependence.DownloadDependenciesGraph.ExDownloadDeadlock;
import com.aerofs.daemon.core.protocol.dependence.DownloadDeadlockResolver;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.daemon.core.ex.ExNoAvailDevice;
import com.aerofs.lib.id.SOCID;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.slf4j.Logger;

import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.Util;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.base.ex.ExNoResource;
import com.aerofs.base.id.DID;
import javax.annotation.Nullable;

// downloads are preemptive. That is, downloads may interrupt others with lower
// priorities. the download thread being interrupted will throw ExAborted with
// cause ExNoResource.

public class Downloads
{
    private static final Logger l = Loggers.getLogger(Downloads.class);

    /**
     * Collector makes a distinction between transient and permanent errors to avoid repeatedly
     * querying a peer about a component. However if the permanent error is encountered along a
     * dependency chain (CONTENT dep on META for instance) the error was not correctly propagated.
     * The component was therefore repeatedly requested and the bloom filters were not cleared
     * leading to significant bandwidth usage, especially for users with a large number of ghost
     * KMLs.
     */
    static class ExNoAvailDeviceForDep extends ExNoAvailDevice
    {
        private static final long serialVersionUID = 0;
        public final SOCID _socid;
        public final Map<DID, Exception> _did2e;
        ExNoAvailDeviceForDep(SOCID socid, Map<DID, Exception> did2e)
        {
            _socid = socid;
            _did2e = did2e;
        }

        @Override
        public String toString()
        {
            return "noDev4Dep:" + _socid + " " + _did2e;
        }
    }

    private class SyncDownloadImpl implements IDownloadCompletionListener
    {
        private TCB _tcb;
        private DID _from;

        private final DependencyEdge _dependency;
        private final To _to;

        private SyncDownloadImpl(DependencyEdge dependency, To to)
        {
            _dependency = dependency;
            _to = to;
        }

        public void invoke(Token tk) throws Exception
        {
            Token tkWait = _tc.acquireThrows_(Cat.UNLIMITED, "syncDL");
            try {
                downloadAsyncThrows_(_dependency.dst, _to, this, tk);
                _tcb = TC.tcb();
                tkWait.pause_("syncDL " + _dependency.dst);
            } catch (ExAborted e) {
                throw (Exception) e.getCause();
            } finally {
                tkWait.reclaim_();
                _tcb = null;
            }

            assert _from != null;
        }

        @Override
        public void onGeneralError_(SOCID socid, Exception e)
        {
            abortWithError(e);
        }

        @Override
        public void onPerDeviceErrors_(SOCID socid, Map<DID, Exception> did2e)
        {
            // Technically this method is only caused by an ExNoAvailDevice
            // We need to propagate the error that caused the failure of this download to make
            // sure the download chain that it is part of will skip all devices for which there
            // was a permanent error (and eventually be aborted if all devices fail permanently)
            abortWithError(new ExNoAvailDeviceForDep(socid, did2e));
        }

        @Override
        public void onDownloadSuccess_(SOCID socid, DID from)
        {
            _from = from;
            if (_tcb != null) _tcb.resume_();
        }

        @Override
        public void onPartialDownloadSuccess_(SOCID socid, DID didFrom) { }

        private void abortWithError(Exception e)
        {
            if (_tcb != null) _tcb.abort_(e);
        }
    }

    private TC _tc;
    private CoreScheduler _sched;
    private CoreQueue _q;
    private Download.Factory _factDownload;
    private To.Factory _factTo;
    private DirectoryService _ds;

    // A global directed graph representing dependencies from ongoing downloads. It is used to
    // detect dependency deadlocks, and pass cycles to the DownloadDeadlockResolver.
    private DownloadDependenciesGraph _dlOngoingDependencies;
    private DownloadDeadlockResolver _ddr;

    private final Map<SOCID, Download> _ongoing = Maps.newTreeMap();

    @Inject
    public void inject_(CoreQueue q, TC tc,
            CoreScheduler sched, Download.Factory factDownload,
            To.Factory factTo, DirectoryService ds, DownloadDependenciesGraph dldg,
            DownloadDeadlockResolver ddr)
    {
        _q = q;
        _tc = tc;
        _sched = sched;
        _factDownload = factDownload;
        _factTo = factTo;
        _ds = ds;
        _dlOngoingDependencies = dldg;
        _ddr = ddr;
    }

    public boolean isOngoing_(SOCID socid)
    {
        return _ongoing.containsKey(socid);
    }

    /**
     * @param dependent the object that depends on the downloading of socid. this is
     * to detect cyclic downloadSync calls (deadlocks).
     */
    public @Nullable DID downloadSync_(SOCID socid, To to, @Nullable Token tk, SOCID dependent)
        throws Exception
    {
        DependencyEdge dependency = new DependencyEdge(dependent, socid);
        return downloadSync_(dependency, to, tk);
    }

    public @Nullable DID downloadSync_(DependencyEdge dependency, To to, Token tk)
            throws Exception
    {
        try {
            // Edges are added to the dependency graph here instead of in Download.java because
            // there are callers of downloadSync_, in addition to Download.java
            _dlOngoingDependencies.addEdge_(dependency);
            SyncDownloadImpl sdi = new SyncDownloadImpl(dependency, to);
            sdi.invoke(tk);
            return sdi._from;
        } catch (ExDownloadDeadlock edldl) {
            // Try to resolve the deadlock, or else crash the app in this method
            _ddr.resolveDeadlock_(edldl._cycle);
            return null;
        } finally {
            // Edges are removed here because some download dependencies (in EmigrationDetector)
            // will add the same edge multiple times for a single download. Alternatively all
            // outward edges for a SOCID could be removed at the end of Download.do_, but this would
            // conflict with the latter download behaviour of EmigrationDetector.
            _dlOngoingDependencies.removeEdge_(dependency);
        }
    }

    public Download downloadAsyncThrows_(SOCID socid, To to, IDownloadCompletionListener listener,
            Token tk) throws ExNoResource
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
            IDownloadCompletionListener listener, @Nullable final Token tk)
    {
        try {
            // assert that the object is not expelled if the branch being downloaded is not metadata
            OA oa;
            assert socid.cid().isMeta() ||
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
                l.debug("cat full 4 " + socid);
                return null;
            }

            l.debug("socid:{}", socid);

            final Download dl = _factDownload.create_(socid, to, listener, tk2);

            IEvent ev = new AbstractEBSelfHandling() {
                    @Override
                    public void handle_()
                    {
                        try {
                            // FIXME (AG): dl.do_ appears to return an exception
                            // we're ignoring it here - is that actually the right thing to do?
                            // talk to MJ about this
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

            return dl;
        }
    }
}

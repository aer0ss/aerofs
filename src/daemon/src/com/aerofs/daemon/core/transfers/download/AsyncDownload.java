/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.transfers.download;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.daemon.core.protocol.*;
import com.aerofs.ids.DID;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.collector.ExNoComponentWithSpecifiedVersion;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.ex.ExNoAvailDevice;
import com.aerofs.daemon.core.ex.ExOutOfSpace;
import com.aerofs.daemon.core.net.To;
import com.aerofs.daemon.core.polaris.db.CentralVersionDatabase;
import com.aerofs.daemon.core.polaris.db.RemoteContentDatabase;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.transfers.download.dependence.DownloadDeadlockResolver;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgUsePolaris;
import com.aerofs.lib.id.SOCID;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.aerofs.daemon.core.transfers.download.Downloads.ExNoAvailDeviceForDep;

/**
 * The AsyncDownload class is in charge of fetching a given component from a set of peers
 *
 * {@link Downloads} manages in-progress downloads and should be the only place where
 * AsyncDownload objects are created.
 *
 * AsyncDownload requests are issued by the {@link com.aerofs.daemon.core.collector.Collector},
 * which currently expects that either:
 * 1. the AsyncDownload request is retried until all KMLs are resolved
 * 2. all devices passed in the download requests are contacted
 *
 * This contract is required to allow the Collector to clean up bloom filters as it goes
 * through the queue of objects to be downloaded. This approach is not without problems
 * (esp re: ghost KMLs and other error cases) and might need to be revisited at some point.
 *
 *
 * Downloads can run into dependencies. Two types of dependencies are distinguished:
 * 1. "Device-specific", where we only ask the device whose reply lead us to discover the
 * dependency. These dependencies can be further classified in PARENT, NAME_CONFLICT and
 * EMIGRATION classes.
 * 2. "Non-specific", where any device advertising the object can be queried
 *
 * The reason for this specificity is that some dependency chains requires strict flow
 * control and may lead to drastic actions (such as aliasing and renaming) which should
 * only be taken when we are absolutely certain they are appropriate.
 *
 * Each AsyncDownload object maintains its own DAG of dependencies and may resolve deadlocks
 * if needed. See:
 * {@link com.aerofs.daemon.core.transfers.download.dependence.DownloadDependenciesGraph}
 * {@link com.aerofs.daemon.core.transfers.download.dependence.DownloadDeadlockResolver}
 *
 * Two concurrent downloads may have conflicting dependencies graphs but that is not an issue as
 * all work touching the core DB is effectively serialized through the core lock. In the worst case
 * one of these downloads may fail and have to be restarted if some of the objects in its graph are
 * changed in ways that break some assumptions in the download pipeline.
 */
class AsyncDownload extends Download
{
    private final Factory _f;

    private final List<IDownloadCompletionListener> _listeners = Lists.newArrayList();

    private final Map<DID, Exception> _did2e = Maps.newHashMap();

    public static class RemoteChangeChecker
    {
        private final CfgUsePolaris _usePolaris;
        private final NativeVersionControl _nvc;
        private final CentralVersionDatabase _cvdb;
        private final RemoteContentDatabase _rcdb;

        @Inject
        public RemoteChangeChecker(CfgUsePolaris usePolaris, RemoteContentDatabase rcdb,
                CentralVersionDatabase cvdb, NativeVersionControl nvc)
        {
            _usePolaris = usePolaris;
            _nvc = nvc;
            _cvdb = cvdb;
            _rcdb = rcdb;
        }

        boolean hasRemoteChanges_(SOCID socid) throws SQLException
        {
            if (_usePolaris.get()) {
                Long v = _cvdb.getVersion_(socid.sidx(), socid.oid());
                return _rcdb.hasRemoteChanges_(socid.sidx(), socid.oid(), v != null ? v : 0);
            } else {
                return !_nvc.getKMLVersion_(socid).isZero_();
            }
        }
    }

    public static class Factory extends Download.Factory
    {
        private final RemoteChangeChecker _changes;

        @Inject
        public Factory(DirectoryService ds, DownloadState dlstate, Downloads dls,
                GetComponentRequest gcc, GetComponentResponse gcr, To.Factory factTo,
                DownloadDeadlockResolver ddr, IMapSIndex2SID sidx2sid, RemoteChangeChecker changes,
                CfgUsePolaris usePolaris, GetContentRequest pgcc, GetContentResponse pgcr)
        {
            super(ds, dlstate, dls, factTo, gcc, gcr, ddr, sidx2sid, usePolaris, pgcc, pgcr);
            _changes = changes;
        }

        AsyncDownload create_(SOCID socid, Set<DID> dids, IDownloadCompletionListener listener,
                @Nonnull Token tk)
        {
            return new AsyncDownload(this, socid, _factTo.create_(dids), listener, tk);
        }
    }

    AsyncDownload(Factory f, SOCID socid, To from, IDownloadCompletionListener listener,
            @Nonnull Token tk)
    {
        super(f, socid, from, tk);
        _listeners.add(listener);
        _f = f;
    }

    void include_(Set<DID> dids, IDownloadCompletionListener completionListener)
    {
        dids.forEach(_from::add_);
        _listeners.add(completionListener);
    }

    /**
     * Try to download the target object until no KMLs are left or all devices have been tried
     * and inform listeners of success/failure appropriately
     */
    void do_()
    {
        try {
            final DID replier = doImpl_();
            notifyListeners_(listener -> listener.onDownloadSuccess_(_socid, replier));
        } catch (ExNoAvailDevice e) {
            l.warn(_socid + ": " + Util.e(e, ExNoAvailDevice.class));
            // This download object tracked all reasons (Exceptions) for why each device was
            // avoided. Thus if the To object indicated no devices were available, then inform
            // the listener about all attempted devices, and why they failed to deliver the socid.
            notifyListeners_(listener -> listener.onPerDeviceErrors_(_socid, _did2e));
        } catch (RuntimeException e) {
            // we don't want the catch-all block to swallow runtime exceptions
            SystemUtil.fatal(e);
        } catch (final Exception e) {
            l.warn("{} :", _socid, BaseLogUtil.suppress(e, ExNoPerm.class));
            notifyListeners_(listener -> listener.onGeneralError_(_socid, e));
        } finally {
            _tk.reclaim_();
        }
    }

    /**
     * Try to download the target object until no KMLs are left or all devices have been tried
     */
    private @Nullable DID doImpl_()
            throws IOException, SQLException, ExAborted, ExNoAvailDevice, ExUnsatisfiedDependency,
            ExNoPerm, ExSenderHasNoPerm, ExOutOfSpace
    {
        while (true) {
            try {
                // reset download context before every attempt
                _cxt = new Cxt();

                // NB: the return value cannot be used in the exception handlers, use
                // _cxt.did instead but be careful as it will be null if the exception is thrown
                // before a successful remote call
                final DID replier = download_();

                notifyListeners_(listener -> listener.onPartialDownloadSuccess_(_socid, replier));

                if (!_f._changes.hasRemoteChanges_(_socid)) return replier;

                l.debug("kml > 0 for {}. dl again", _socid);

                // TODO: indicate success for this DID somehow (null exception or something else)
                //_did2e.put(replier, null);

                // NB: this really shouldn't be necessary as To.pick_() already calls avoid_()
                _from.avoid_(replier);

            } catch (ExRemoteCallFailed e) {
                // NB: remote errors in the GCR are wrapped in ExProcessReplyFailed...
                l.info("gcc fail {}: {}", _socid, Util.e(e._e));
                onGeneralException(e._e, null);
            } catch (ExProcessReplyFailed e) {
                if (e._e instanceof ExNoPerm) {
                    // collector should only collect permitted components. no_perm may happen when
                    // other user just changed the permission before this call.
                    l.error(_socid + ": we have no perm");
                    throw (ExNoPerm)e._e;
                } else if (e._e instanceof ExSenderHasNoPerm) {
                    l.error(_socid + ": sender has no perm");
                    avoidDevice_(e._did, e);
                } else if (e._e instanceof ExNoComponentWithSpecifiedVersion) {
                    l.info("{} from {}: {}", _socid, e._did,
                            Util.e(e._e, ExNoComponentWithSpecifiedVersion.class));
                    avoidDevice_(e._did, e._e);
                } else if (e._e instanceof ExOutOfSpace) {
                    throw (ExOutOfSpace)e._e;
                } else if (e._e instanceof IOException) {
                    // TODO: make sure we only abort in case of local I/O error
                    throw (IOException)e._e;
                } else {
                    l.info("gcr fail {} from {}: {}", _socid, e._did, Util.e(e._e));

                    onGeneralException(e._e, e._did);
                }
            } catch (ExUnsatisfiedDependency e) {
                if (e._did == null) {
                    // a non-specific dependency (i.e. CONTENT->META) could not be resolved
                    if (e._e instanceof ExNoAvailDeviceForDep) {
                        // rewrap per-device exception such that the Collector can correctly check
                        // for permanent errors on the chain
                        Map<DID, Exception> did2e = ((ExNoAvailDeviceForDep)e._e)._did2e;
                        l.info("unsat dep {}->{} from ?: {}", _socid, e._socid, did2e);
                        for (DID did : did2e.keySet()) {
                            if (!_did2e.containsKey(did)) {
                                avoidDevice_(did,
                                        new ExUnsatisfiedDependency(e._socid, did, did2e.get(did)));
                            }
                        }
                        throw new ExNoAvailDevice();
                    } else {
                        l.info("unsat dep {}->{} from ?: {}", _socid, e._socid, Util.e(e._e));
                        throw e;
                    }
                } else {
                    // the dependency chain could not be followed for a device, try another
                    l.info("unsat dep {}->{} from {}: {}", _socid, e._socid, e._did, Util.e(e._e,
                            // suppress its stack trace since it has caused huge logs in Bloomberg
                            // filled with traces from this error:
                            //
                            // com.aerofs.ExProcessReplyFailed: com.aerofs.ExFileNotFound:
                            //    file /f2e88c90/8a08fc0c/3d9d184b/f75b2303 not found (attrs:edrwxav,edrwxav)
                            //
                            // TODO (WW) Remove it as soon as the root cause of the bug is fixed.
                            // See the email thread "Re: huge log output"
                            ExProcessReplyFailed.class));
                    avoidDevice_(e._did, e);
                }
            }
        }
    }

    private static interface IDownloadCompletionListenerVisitor
    {
        void notify_(IDownloadCompletionListener l);
    }

    private void notifyListeners_(IDownloadCompletionListenerVisitor visitor)
    {
        l.debug("notify {} listeners", _listeners.size());
        for (IDownloadCompletionListener lst : _listeners) {
            l.debug("  notify {}", lst);
            visitor.notify_(lst);
        }
    }

    private void onGeneralException(Exception e, DID replier)
    {
        if (e instanceof RuntimeException) SystemUtil.fatal(e);

        // RTN: retry now
        l.warn(_socid + ": " + Util.e(e) + " " + replier + " RTN");
        if (replier != null) avoidDevice_(replier, e);
    }

    protected void avoidDevice_(DID replier, Exception e)
    {
        // NB: is this really necessary?
        // To.pick_() already calls avoid_() so the only case where a second call makes a difference
        // is when a new download request was made for the same object while the core lock was
        // released around a remote call. I can't help but wonder if calling avoid_() in such a case
        // is actually a bug...
        _from.avoid_(replier);
        _did2e.put(replier, e);
    }

    @Override
    public String toString()
    {
        return "AsyncDL(" + _socid + "," + _from + "," + _cxt + ")";
    }
}

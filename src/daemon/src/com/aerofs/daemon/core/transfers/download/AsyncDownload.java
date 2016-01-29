/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.transfers.download;

import com.aerofs.base.BaseLogUtil;
import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.daemon.core.NativeVersionControl;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.ex.ExNoAvailDevice;
import com.aerofs.daemon.core.ex.ExOutOfSpace;
import com.aerofs.daemon.core.net.To;
import com.aerofs.daemon.core.polaris.db.CentralVersionDatabase;
import com.aerofs.daemon.core.polaris.db.RemoteContentDatabase;
import com.aerofs.daemon.core.protocol.*;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.transfers.download.dependence.DownloadDeadlockResolver;
import com.aerofs.ids.DID;
import com.aerofs.lib.cfg.CfgUsePolaris;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOID;
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
public class AsyncDownload extends Download implements IAsyncDownload
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

    public static class Factory extends Download.Factory implements IAsyncDownloadFactory
    {
        private final RemoteChangeChecker _changes;

        @Inject
        public Factory(DirectoryService ds, Downloads dls,
                GetComponentRequest gcc, GetComponentResponse gcr, To.Factory factTo,
                DownloadDeadlockResolver ddr, IMapSIndex2SID sidx2sid, RemoteChangeChecker changes,
                CfgUsePolaris usePolaris, GetContentRequest pgcc, GetContentResponse pgcr)
        {
            super(ds, dls, factTo, gcc, gcr, ddr, sidx2sid, usePolaris, pgcc, pgcr);
            _changes = changes;
        }

        // TODO(AS): Remove when only polaris is in in business exclusively.
        IAsyncDownload create_(SOCID socid, Set<DID> dids, IDownloadCompletionListener listener,
                @Nonnull Token tk)
        {
            return new AsyncDownload(this, socid, _factTo.create_(dids), listener, tk);
        }

        // TODO(AS) Right now only useful for testing but once polaris is in business, this is
        // going to be used exclusively.
        @Override
        public IAsyncDownload create_(SOID soid, Set<DID> dids,
                IDownloadCompletionListener listener, @Nonnull Token tk)
        {
            return new AsyncDownload(this, new SOCID(soid, CID.CONTENT), _factTo.create_(dids), listener, tk);
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
        include_(dids, completionListener, _from, _listeners);
    }

    /**
     * Try to download the target object until no KMLs are left or all devices have been tried
     * and inform listeners of success/failure appropriately
     */
    void do_()
    {
        do_(_socid, _tk,_did2e, _listeners);
    }

    /**
     * Try to download the target object until no KMLs are left or all devices have been tried
     */
    @Override
    @Nullable
    public DID doImpl_() throws IOException, SQLException, ExAborted, ExNoAvailDevice,
            ExUnsatisfiedDependency, ExNoPerm, ExSenderHasNoPerm, ExOutOfSpace
    {
        while (true) {
            try {
                // reset download context before every attempt
                _cxt = new Cxt();

                // NB: the return value cannot be used in the exception handlers, use
                // _cxt.did instead but be careful as it will be null if the exception is thrown
                // before a successful remote call
                final DID replier = download_();

                notifyListeners_(listener -> listener.onPartialDownloadSuccess_(_socid, replier),
                        _listeners);

                if (!_f._changes.hasRemoteChanges_(_socid)) return replier;

                l.debug("kml > 0 for {}. dl again", _socid);

                // TODO: indicate success for this DID somehow (null exception or something else)
                //_did2e.put(replier, null);

                // NB: this really shouldn't be necessary as To.pick_() already calls avoid_()
                _from.avoid_(replier);
            } catch (ExRemoteCallFailed e) {
                handleRemoteCallFailed(e, _socid, _from, _did2e);
            } catch (ExProcessReplyFailed e) {
                handleProcessReplyFailed(e, _socid, _from, _did2e);
            } catch (ExUnsatisfiedDependency e) {
                handleUnsatisfiedDepedency(e);
            }
        }
    }

    private void handleUnsatisfiedDepedency(ExUnsatisfiedDependency e) throws ExNoAvailDevice,
            ExUnsatisfiedDependency
    {
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
                l.info("unsat dep {}->{} from ?:", _socid, e._socid, e._e);
                throw e;
            }
        } else {
            // the dependency chain could not be followed for a device, try another
            l.info("unsat dep {}->{} from {}: ", _socid, e._socid, e._did, BaseLogUtil.suppress(e._e,
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

    protected void avoidDevice_(DID replier, Exception e)
    {
        avoidDevice_(replier, e, _from, _did2e);
    }

    @Override
    public String toString()
    {
        return "AsyncDL(" + _socid + "," + _from + "," + _cxt + ")";
    }
}

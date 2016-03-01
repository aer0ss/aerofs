/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.transfers.download;

import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.daemon.core.collector.ContentFetcher;
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
import com.aerofs.ids.DID;
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

/**
 * The AsyncDownload class is in charge of fetching a given object from a set of peers
 *
 * {@link Downloads} manages in-progress downloads and should be the only place where
 * AsyncDownload objects are created.
 *
 * AsyncDownload requests are issued by the {@link ContentFetcher},
 * which currently expects that either:
 * 1. the AsyncDownload request is retried until the most up-to-date remote version is fetched
 * 2. all devices passed in the download requests are contacted
 *
 * This contract is required to allow the Collector to clean up bloom filters as it goes
 * through the queue of objects to be downloaded. This approach is not without problems
 * (esp re: ghost KMLs and other error cases) and might need to be revisited at some point.
 */
public class AsyncDownload extends Download implements IAsyncDownload
{
    private final Factory _f;

    private final List<IDownloadCompletionListener> _listeners = Lists.newArrayList();

    private final Map<DID, Exception> _did2e = Maps.newHashMap();

    public static class RemoteChangeChecker
    {
        private final CentralVersionDatabase _cvdb;
        private final RemoteContentDatabase _rcdb;

        @Inject
        public RemoteChangeChecker(RemoteContentDatabase rcdb, CentralVersionDatabase cvdb)
        {
            _cvdb = cvdb;
            _rcdb = rcdb;
        }

        boolean hasRemoteChanges_(SOID soid) throws SQLException
        {
            Long v = _cvdb.getVersion_(soid.sidx(), soid.oid());
            return _rcdb.hasRemoteChanges_(soid.sidx(), soid.oid(), v != null ? v : 0);
        }
    }

    public static class Factory extends Download.Factory implements IAsyncDownloadFactory
    {
        private final RemoteChangeChecker _changes;

        @Inject
        public Factory(DirectoryService ds, Downloads dls, To.Factory factTo,
                       IMapSIndex2SID sidx2sid, RemoteChangeChecker changes,
                       GetContentRequest pgcc, GetContentResponse pgcr)
        {
            super(ds, dls, factTo, sidx2sid, pgcc, pgcr);
            _changes = changes;
        }

        @Override
        public IAsyncDownload create_(SOID soid, Set<DID> dids,
                IDownloadCompletionListener listener, @Nonnull Token tk)
        {
            return new AsyncDownload(this, soid, _factTo.create_(dids), listener, tk);
        }
    }

    AsyncDownload(Factory f, SOID soid, To from, IDownloadCompletionListener listener,
            @Nonnull Token tk)
    {
        super(f, soid, from, tk);
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
        do_(_soid, _tk,_did2e, _listeners);
    }

    /**
     * Try to download the target object until no KMLs are left or all devices have been tried
     */
    @Override
    @Nullable
    public DID doImpl_() throws IOException, SQLException, ExAborted, ExNoAvailDevice,
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

                notifyListeners_(listener -> listener.onPartialDownloadSuccess_(_soid, replier),
                        _listeners);

                if (!_f._changes.hasRemoteChanges_(_soid)) return replier;

                l.debug("kml > 0 for {}. dl again", _soid);

                // TODO: indicate success for this DID somehow (null exception or something else)
                //_did2e.put(replier, null);

                // NB: this really shouldn't be necessary as To.pick_() already calls avoid_()
                _from.avoid_(replier);
            } catch (ExRemoteCallFailed e) {
                handleRemoteCallFailed(e, _soid, _from, _did2e);
            } catch (ExProcessReplyFailed e) {
                handleProcessReplyFailed(e, _soid, _from, _did2e);
            }
        }
    }

    @Override
    public String toString()
    {
        return "AsyncDL(" + _soid + "," + _from + "," + _cxt + ")";
    }
}

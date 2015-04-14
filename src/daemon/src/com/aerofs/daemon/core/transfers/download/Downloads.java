/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.transfers.download;

import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.tc.*;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.ids.DID;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgUsePolaris;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.id.CID;
import com.aerofs.lib.id.SOCID;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.slf4j.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Manages in-progress {@link AsyncDownload} objects.
 */
public class Downloads implements IContentDownloads
{
    private static final Logger l = Loggers.getLogger(Downloads.class);

    private TokenManager _tokenManager;
    private AsyncDownload.Factory _factDL;

    private CoreScheduler _sched;

    private CfgUsePolaris _usePolaris;

    private final Map<SOCID, AsyncDownload> _ongoing = Maps.newTreeMap();

    static class ExNoAvailDeviceForDep extends Exception
    {
        private static final long serialVersionUID = 0L;
        public final Map<DID, Exception> _did2e;
        ExNoAvailDeviceForDep(Map<DID, Exception> did2e) { _did2e = did2e; }
    }

    @Inject
    public void inject_(CoreScheduler sched, TokenManager tokenManager,
            AsyncDownload.Factory factDL, CfgUsePolaris usePolaris)
    {
        _sched = sched;
        _factDL = factDL;
        _tokenManager = tokenManager;
        _usePolaris = usePolaris;
    }


    @Override
    public boolean downloadAsync_(SOID soid, Set<DID> dids,
                           ITokenReclamationListener cb,
                           IDownloadCompletionListener l)
    {
        return downloadAsync_(new SOCID(soid, CID.CONTENT), dids, cb, l);
    }

    /**
     * Make a download request
     * @return true on success
     *
     * If a download request already exists for the given object, the given set of DIDs will
     * simply be added to it and the request is considered successful.
     *
     * If no {@link com.aerofs.daemon.core.tc.Token} can be obtained (i.e. the max number of
     * concurrent download was reached), the request is considered unsuccessful and the given
     * token reclamation listener will be called when a download slots becomes free.
     */
    public boolean downloadAsync_(SOCID socid, Set<DID> dids,
            ITokenReclamationListener continuationCallback,
            IDownloadCompletionListener completionListener)
    {
        AsyncDownload dl = _ongoing.get(socid);

        // add devices and completion listener to existing download
        if (dl != null) {
            dl.include_(dids, completionListener);
            return true;
        }

        // try to acquire download token
        Token tk = _tokenManager.acquire_(Cat.CLIENT, "dl " + socid);
        if (tk == null) {
            _tokenManager.addTokenReclamationListener_(Cat.CLIENT, continuationCallback);
            return false;
        }

        downloadAsync_(socid, dids, completionListener, tk);
        return true;
    }

    /**
     * Synchronously download an object
     *
     * for CONTENT->META dep we allow indiscriminate resolution (i.e. download from any peer) and
     * we want to avoid duplicate downloads so we use {@link Downloads} to enqueue a new download
     * or add devices to an existing one as needed.
     */
    void downloadSync_(SOCID socid, final Set<DID> dids, final IDownloadContext cxt)
            throws ExAborted, ExUnsatisfiedDependency
    {
        assert socid.cid().isMeta() : socid;
        assert !dids.isEmpty() : socid + " " + cxt;

        try {
            if (socid.cid().isMeta() && _usePolaris.get()) {
                throw new ExBadArgs("no p2p meta transfer when polaris enabled");
            }
        } catch (Exception e) {
            throw new ExUnsatisfiedDependency(socid, null, e);
        }

        final TCB tcb = TC.tcb();
        final OutArg<Exception> ex = new OutArg<Exception>();
        IDownloadCompletionListener listener = new IDownloadCompletionListener() {
            private TCB _tcb = tcb;

            private void resume_(@Nullable Exception e)
            {
                if (_tcb != null) {
                    ex.set(e);
                    _tcb.resume_();
                    _tcb = null;
                }
            }

            @Override
            public void onPartialDownloadSuccess_(SOCID socid, DID didFrom)
            {
                // we do not need to resolve all KMLs
                // as soon as the object is present locally we can resume the caller
                resume_(null);
            }

            @Override
            public void onDownloadSuccess_(SOCID socid, DID from)
            {}

            @Override
            public void onPerDeviceErrors_(SOCID socid, Map<DID, Exception> did2e)
            {
                l.debug("dev dep error {} {} {}", socid, dids, did2e);
                resume_(new ExNoAvailDeviceForDep(did2e));
            }

            @Override
            public void onGeneralError_(SOCID socid, Exception e)
            {
                l.debug("gen dep error {} {} {}", socid, dids, e);
                resume_(e);
            }

            @Override
            public String toString()
            {
                return "DLdep(" + cxt + ")";
            }
        };

        AsyncDownload dl = _ongoing.get(socid);

        // add devices and completion listener to existing download
        if (dl != null) {
            dl.include_(dids, listener);
        } else {
            // we can use UNLIMITED tokens here because the caller MUST be an AsyncDownload holding
            // a CLIENT token which is *not* reclaimed during the synchronous download
            Token tk = _tokenManager.acquire_(Cat.UNLIMITED, "syncdl " + socid);
            downloadAsync_(socid, dids, listener, tk);
        }

        // pause the calling thread until the dependency is downloaded
        cxt.token().pause_("wait for dep");
        if (ex.get() != null) throw new ExUnsatisfiedDependency(socid, null, ex.get());
    }

    private void downloadAsync_(SOCID socid, Set<DID> dids, IDownloadCompletionListener listener,
            @Nonnull Token tk)
    {
        l.debug("socid:{}", socid);
        AsyncDownload dl = (AsyncDownload) _factDL.create_(socid, dids, listener, tk);

        // try to immediately enqueue event, schedule if core queue full
        IEvent ev = makeDownloadEvent_(dl);
        _sched.schedule_(ev);

        Util.verify(_ongoing.put(socid, dl) == null);
    }

    /**
     * Create a core event that will perform the download
     */
    private IEvent makeDownloadEvent_(final AsyncDownload dl)
    {
        return new AbstractEBSelfHandling() {
            @Override
            public void handle_()
            {
                try {
                    dl.do_();
                } finally {
                    checkNotNull(_ongoing.remove(dl._socid));
                }
            }
        };
    }
}

/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.download;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.CoreQueue;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.ex.ExAborted;
import com.aerofs.daemon.core.ex.ExNoAvailDevice;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.ITokenReclamationListener;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.Util;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.id.SOCID;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

/**
 * Manages in-progress {@link AsyncDownload} objects.
 */
public class Downloads
{
    private static final Logger l = Loggers.getLogger(Downloads.class);

    private TokenManager _tokenManager;
    private AsyncDownload.Factory _factDL;

    private CoreQueue _q;
    private CoreScheduler _sched;

    private final Map<SOCID, AsyncDownload> _ongoing = Maps.newTreeMap();

    @Inject
    public void inject_(CoreQueue q, CoreScheduler sched, TokenManager tokenManager,
            AsyncDownload.Factory factDL)
    {
        _q = q;
        _sched = sched;
        _factDL = factDL;
        _tokenManager = tokenManager;
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
    void downloadSync_(SOCID socid, Set<DID> dids, IDownloadContext cxt)
            throws ExAborted, ExUnsatisfiedDependency
    {
        assert socid.cid().isMeta() : socid;

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
                resume_(new ExNoAvailDevice());
            }

            @Override
            public void onGeneralError_(SOCID socid, Exception e)
            {
                resume_(e);
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
        AsyncDownload dl = _factDL.create_(socid, dids, listener, tk);

        // try to immediately enqueue event, schedule if core queue full
        IEvent ev = makeDownloadEvent_(dl);
        if (!_q.enqueue_(ev, Prio.LO)) _sched.schedule(ev, 0);

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
                    Util.verify(_ongoing.remove(dl._socid));
                }
            }
        };
    }
}

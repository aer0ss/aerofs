/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.transfers.download;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.CoreScheduler;
import com.aerofs.daemon.core.tc.*;
import com.aerofs.ids.DID;
import com.aerofs.lib.Util;
import com.aerofs.lib.event.AbstractEBSelfHandling;
import com.aerofs.lib.event.IEvent;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import org.slf4j.Logger;
import javax.annotation.Nonnull;
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

    private final Map<SOID, AsyncDownload> _ongoing = Maps.newTreeMap();

    @Inject
    public void inject_(CoreScheduler sched, TokenManager tokenManager,
            AsyncDownload.Factory factDL)
    {
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
    @Override
    public boolean downloadAsync_(SOID soid, Set<DID> dids,
            ITokenReclamationListener continuationCallback,
            IDownloadCompletionListener completionListener)
    {
        AsyncDownload dl = _ongoing.get(soid);

        // add devices and completion listener to existing download
        if (dl != null) {
            dl.include_(dids, completionListener);
            return true;
        }

        // try to acquire download token
        Token tk = _tokenManager.acquire_(Cat.CLIENT, "dl " + soid);
        if (tk == null) {
            _tokenManager.addTokenReclamationListener_(Cat.CLIENT, continuationCallback);
            return false;
        }

        downloadAsync_(soid, dids, completionListener, tk);
        return true;
    }

    private void downloadAsync_(SOID soid, Set<DID> dids, IDownloadCompletionListener listener,
            @Nonnull Token tk)
    {
        l.debug("socid:{}", soid);
        AsyncDownload dl = (AsyncDownload) _factDL.create_(soid, dids, listener, tk);

        // try to immediately enqueue event, schedule if core queue full
        IEvent ev = makeDownloadEvent_(dl);
        _sched.schedule(ev);

        Util.verify(_ongoing.put(soid, dl) == null);
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
                    checkNotNull(_ongoing.remove(dl._soid));
                }
            }
        };
    }
}

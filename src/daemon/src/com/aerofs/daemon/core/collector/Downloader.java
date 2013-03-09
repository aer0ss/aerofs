/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.collector;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.daemon.core.net.To;
import com.aerofs.daemon.core.protocol.Downloads;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.ITokenReclamationListener;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.lib.Util;
import com.aerofs.lib.id.SOCID;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.util.Set;

/**
 * abstracts the interaction with the download subsystem away from {@link Collector} to reduce
 * coupling and make testing easier
 *
 * TODO: merge with Downloads?
 */
class Downloader
{
    private static final Logger l = Loggers.getLogger(Downloader.class);

    private final Downloads _dls;
    private final To.Factory _factTo;
    private final TokenManager _tokenManager;

    @Inject
    public Downloader(TokenManager tokenManager, Downloads dls, To.Factory factTo)
    {
        _dls = dls;
        _factTo = factTo;
        _tokenManager = tokenManager;
    }

    public boolean downloadAsync_(SOCID socid, Set<DID> dids, IDownloadListenerFactory dlf,
            ITokenReclamationListener continuationCallback)
    {
        final Token tk;
        if (_dls.isOngoing_(socid)) {
            // no token is needed for ongoing downloads
            tk = null;
        } else {
            Cat cat = _dls.getCat();
            tk = _tokenManager.acquire_(cat, "collect " + socid);
            if (tk == null) {
                l.debug("request continuation");
                _tokenManager.addTokenReclamationListener_(cat, continuationCallback);
                return false;
            }
        }

        // to guarantee tk is reclaimed properly, no code should be added here

        boolean enqueued = false;
        try {
            // download the component even if it's already being downloaded, so
            // that the new destination devices can be added if they are not there
            Util.verify(_dls
                    .downloadAsync_(socid, _factTo.create_(dids), dlf.create_(dids, tk), tk));

            enqueued = true;
            return true;
        } finally {
            if (tk != null && !enqueued) tk.reclaim_();
        }
    }
}

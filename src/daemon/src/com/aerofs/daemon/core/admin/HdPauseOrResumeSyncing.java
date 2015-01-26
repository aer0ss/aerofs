package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.core.status.PauseSync;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.event.admin.EIPauseOrResumeSyncing;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.link.LinkStateService;
import com.aerofs.lib.event.Prio;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HdPauseOrResumeSyncing extends AbstractHdIMC<EIPauseOrResumeSyncing>
{
    private static final Logger l = LoggerFactory.getLogger(HdPauseOrResumeSyncing.class);

    private final TokenManager _tokenManager;
    private final LinkStateService _lss;
    private final PauseSync _pauseSync;

    @Inject
    public HdPauseOrResumeSyncing(TokenManager tokenManager, LinkStateService lss,
            PauseSync pauseSync)
    {
        _tokenManager = tokenManager;
        _lss = lss;
        _pauseSync = pauseSync;
    }

    @Override
    protected void handleThrows_(EIPauseOrResumeSyncing ev, Prio prio) throws Exception
    {
        l.info(ev._pause ? "pause syncing" : "resume syncing");

        // pause polaris interactions
        if (ev._pause) {
            _pauseSync.pause();
        } else {
            _pauseSync.resume();
        }

        _tokenManager.inPseudoPause_(Cat.UNLIMITED, "pause-sync", () -> {
            if (ev._pause) {
                l.debug("initiating pause sync");
                _lss.markLinksDown();
                l.debug("completed pause sync");
            } else {
                l.debug("initiating resume sync");
                _lss.markLinksUp();
                l.debug("completed resume sync");
            }
            return null;
        });
    }
}

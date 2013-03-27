/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.ui;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.SID;
import com.aerofs.cli.CLI;
import com.aerofs.cli.CLIRootAnchorUpdater;
import com.aerofs.gui.GUI;
import com.aerofs.gui.misc.DlgRootAnchorUpdater;
import com.aerofs.gui.shellext.ShellextService;
import com.aerofs.labeling.L;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.ritual.RitualBlockingClient;
import com.aerofs.lib.ritual.RitualClientFactory;
import com.aerofs.ui.IUI.MessageType;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Map.Entry;

/**
 * Periodically checks for existence of:
 * 1. default root anchor
 * 2. any external roots
 *
 * And warns the user if any of them disappears.
 */
public class RootAnchorPoller
{
    private static final Logger l = Loggers.getLogger(RootAnchorPoller.class);

    private Thread _t = null;
    private volatile boolean _stopping = false;

    public void start()
    {
        // S3 storage only uses the prefix folder in the auxroot...
        StorageType storageType = Cfg.storageType();
        if (storageType == StorageType.S3) return;

        // We poll for the existance of the root anchor. We used to use
        // JNotify for this but it cause problems and only gave a negligible
        // gain. So long as our polling interval is short enough that the change
        // to the root anchor location is fresh in the users mind, this will work
        // fine.
        _t = ThreadUtil.startDaemonThread("root-anchor-watch-worker", new Runnable()
        {
            @Override
            public void run()
            {
                while (!_stopping) {
                    check();

                    // Do not use uninterruptable sleep so that stop() can stop us immediately.
                    try {
                        Thread.sleep(UIParam.ROOT_ANCHOR_POLL_INTERVAL);
                    } catch (InterruptedException e) {
                        // If we were interrupted and we have not beeen stopped by the stop()
                        // function, this is bad and we must bail.
                        if (!_stopping) {
                            l.error("Interrupted exception while sleeping in poller: " + Util.e(e));
                            SystemUtil.fatal(e);
                        }
                    }
                }
            }
        });
    }

    private void check()
    {
        checkRoot(Cfg.absDefaultRootAnchor(), null);
        for (Entry<SID, String> e : Cfg.getRoots().entrySet()) checkRoot(e.getValue(), e.getKey());
    }

    // SID is null for default root
    private void checkRoot(String absPath, @Nullable SID sid)
    {
        l.debug("Checking root anchor {} {}", sid, absPath);
        // This must be checked each time in case the rootAnchor is moved
        File rootAnchor = new File(absPath);
        if (!rootAnchor.isDirectory()) {
            l.debug("Root anchor missing at {}", absPath);
            try{
                notifyMissingRootAnchor_(absPath, sid);
            } catch (Exception ex) {
                // We can just warn because we will retry later.
                l.warn("Error occurred while notifying missing root anchor: {}", Util.e(ex));
            }
        }
    }

    /**
     * Stop the poller. It is important that the thread is actually stopped when this function
     * returns, hence the use of interrupt, so that we do not end up with UI notifications if we
     * are unlinking the device.
     */
    public void stop()
    {
        _stopping = true;
        if (_t != null) {
            _t.interrupt();
        }
    }

    /**
     * This method should never return
     *
     * The polling thread will block when calling this method, only showing the dialogue once.
     */
    private void notifyMissingRootAnchor_(final String oldAbsPath, final @Nullable SID sid)
        throws Exception
    {
        blockingRitualCall();

        // Check if a 'move' operation was executed from the 'Preferences' menu.
        if (onPotentialRootAnchorChange_(oldAbsPath, sid)) return;

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (UI.isGUI()) {
                    new DlgRootAnchorUpdater(GUI.get().sh(), oldAbsPath, sid).openDialog();
                } else {
                    new CLIRootAnchorUpdater(CLI.get(), oldAbsPath, sid).ask();
                }
            }
        };
        UI.get().exec(runnable);

        // Check if we successfully changed the location of the root anchor after the user
        // manually moved the root anchor.
        if (onPotentialRootAnchorChange_(oldAbsPath, sid)) {
            try {
                UI.dm().start();  // restart the daemon
                UI.rnc().resume(); // restart ritual notification client
            } catch (Exception e1) {
                GUI.get().show(MessageType.ERROR,
                        "An error occured while starting up " + L.PRODUCT +
                                " " + UIUtil.e2msg(e1));
                l.warn(Util.e(e1));
            }
        }
    }

    /**
     * Call this method if the root anchor is possibly changed. It does everything necessary to
     * react to the change. Note that this method can be called from UI or non-UI threads
     * @return whether the root anchor has actually changed.
     */
    private boolean onPotentialRootAnchorChange_(String oldAbsPath, SID sid)
        throws Exception
    {
        // The user might have changed the root anchor from other processes so we need to reload the
        // configuration.
        Cfg.init_(Cfg.absRTRoot(), false);

        String absPath = sid == null ? Cfg.absDefaultRootAnchor() : Cfg.getRoots().get(sid);
        if (absPath == null) {
            // (external root) root disappeared: ignore
            assert sid != null;
            return true;
        }

        if (!new File(absPath).isDirectory()) return false;

        ShellextService.get().notifyRootAnchor();

        // only maintain favorite for default root
        if (sid == null) updateFavorite(oldAbsPath, absPath);
        return true;
    }

    private static void updateFavorite(String oldRoot, String newRoot)
    {
        if (Cfg.storageType() != StorageType.LINKED) return;

        try {
            OSUtil.get().removeFromFavorite(oldRoot);
            OSUtil.get().addToFavorite(newRoot);
        } catch (IOException e) {
            // This is unimportant so we can just warn and move on.
            l.warn("Updating favorites failed: " + e);
        }
    }

    /*
     * Block until the daemon finishes (i.e. if the daemon is still doing any work do not pop up the
     * RAP dialog for relocation until the daemon is done processing).
     * This prevents unnecessary dialogs showing up when relocation of root anchor is in progress.
     */
    private void blockingRitualCall()
    {
        RitualBlockingClient ritual = RitualClientFactory.newBlockingClient();
        try {
            ritual.heartbeat();
        } catch (Exception e) {
            l.warn("Rpc call failure ignored: " + Util.e(e, Exception.class));
        } finally {
            ritual.close();
        }
        UI.dm().stopIgnoreException();
        UI.rnc().pause();
    }
}

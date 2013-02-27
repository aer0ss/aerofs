/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.ui;

import com.aerofs.base.Loggers;
import com.aerofs.cli.CLI;
import com.aerofs.cli.CLIRootAnchorUpdater;
import com.aerofs.gui.GUI;
import com.aerofs.gui.misc.DlgRootAnchorUpdater;
import com.aerofs.gui.shellext.ShellextService;
import com.aerofs.labeling.L;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.ritual.RitualBlockingClient;
import com.aerofs.lib.ritual.RitualClientFactory;
import com.aerofs.ui.IUI.MessageType;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;

public class RootAnchorPoller
{
    private static final Logger l = Loggers.getLogger(RootAnchorPoller.class);

    private Thread _t = null;
    private String _oldRoot;
    private volatile boolean _stopping = false;

    public void start()
    {
        // TODO (KH): Change this to Cfg.useS3() when available
        // defaultValue of S3_DIR is null
        if (Cfg.db().getNullable(Key.S3_BUCKET_ID) != null) {
            return;
        }

        _oldRoot = Cfg.absRootAnchor();

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
                    // This must be checked each time in case the rootAnchor is moved
                    File rootAnchor = new File(Cfg.absRootAnchor());

                    if (l.isDebugEnabled()) {
                        l.debug("Checking for existance of root anchor at " +
                                rootAnchor.getAbsolutePath());
                    }

                    if (!rootAnchor.isDirectory()) {
                        l.debug("Root anchor missing at " + rootAnchor.getAbsolutePath());
                        try{
                            notifyMissingRootAnchor_();
                        } catch (Exception e) {
                            // We can just warn because we will retry later.
                            l.warn("Error occurred while notifying missing root anchor: " + e);
                        }
                    }

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
    private void notifyMissingRootAnchor_()
        throws Exception
    {
        blockingRitualCall();

        // Check if a 'move' operation was executed from the 'Preferences' menu.
        if (onPotentialRootAnchorChange_()) return;

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (UI.isGUI()) new DlgRootAnchorUpdater(GUI.get().sh()).openDialog();
                else new CLIRootAnchorUpdater(CLI.get()).ask();
            }
        };
        UI.get().exec(runnable);

        // Check if we successfully changed the location of the root anchor after the user
        // manually moved the root anchor.
        if (onPotentialRootAnchorChange_()) {
            try {
                UI.dm().start();  // restart the daemon
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
    public boolean onPotentialRootAnchorChange_()
        throws Exception
    {
        // The user might have changed the root anchor from other processes so we need to reload the
        // configuration.
        Cfg.init_(Cfg.absRTRoot(), false);

        File rootAnchor = new File(Cfg.absRootAnchor());
        if (!rootAnchor.isDirectory()) return false;

        ShellextService.get().notifyRootAnchor();

        updateFavorite(_oldRoot, Cfg.absRootAnchor());
        _oldRoot = Cfg.absRootAnchor();

        return true;
    }

    private static void updateFavorite(String oldRoot, String newRoot)
    {
        if (L.get().isMultiuser()) return;

        try {
            OSUtil.get().removeFromFavorite(oldRoot);
            OSUtil.get().addToFavorite(newRoot);
        } catch (IOException e) {
            // This is unimportant so we can just warn and move on.
            l.warn("Updating favorites failed: " + e);
        }
    }

    /*
     * An arbitrary rpc to block until the daemon finishes (i.e. if the daemon is still doing any
     * work do not pop up the RAP dialog for relocation until the daemon is done processing).
     * This prevents unnecessary dialogues showing when relocation of root anchor is in progress.
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
    }
}

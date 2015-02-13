/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.ui;

import com.aerofs.base.Loggers;
import com.aerofs.ids.SID;
import com.aerofs.cli.CLI;
import com.aerofs.cli.CLIRootAnchorUpdater;
import com.aerofs.gui.GUI;
import com.aerofs.gui.misc.DlgRootAnchorUpdater;
import com.aerofs.labeling.L;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.ex.ExNoConsole;
import com.aerofs.lib.obfuscate.ObfuscatingFormatters;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.error.ErrorMessages;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map.Entry;

/**
 * Periodically checks the sanity of the environment:
 *
 * 1. rtroot should have free space (for logs and db)
 * 2. default root anchor and external roots should exist
 * 3. default root anchor and external roots should have free space
 *
 * If any of this conditions is not verified, prevent the daemon from repeatedly respawning
 * (and flooding sv/rocklog with defects) and warn the user.
 */
public class SanityPoller
{
    private static final Logger l = Loggers.getLogger(SanityPoller.class);

    private Thread _t = null;
    private volatile boolean _stopping = false;

    private volatile boolean _ignoreDiskFullErrors; // for users to suppress error notifications

    public static class ShouldProceed
    {
        private boolean _val = false;
        boolean shouldProceed() { return _val; }
        public void proceedIf(boolean predicate) { _val = _val || predicate; }
    }

    public void start()
    {
        // start must be idempotent to handle launch->setup backtracking
        if (_t != null) return;

        // S3 storage only uses the prefix folder in the auxroot...
        StorageType storageType = Cfg.storageType();
        if (storageType == StorageType.S3) return;

        // We poll for the existence of the root anchor. We used to use
        // JNotify for this but it cause problems and only gave a negligible
        // gain. So long as our polling interval is short enough that the change
        // to the root anchor location is fresh in the users mind, this will work
        // fine.
        _t = ThreadUtil.startDaemonThread("sanity", () -> {
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
        });
    }

    private void check()
    {
        checkRTRoot(Cfg.absRTRoot());
        checkRoot(Cfg.absDefaultRootAnchor(), null);
        try {
            for (Entry<SID, String> e : Cfg.getRoots().entrySet()) {
                checkRoot(e.getValue(), e.getKey());
            }
        } catch (SQLException e) {
            l.error("ignored exception", e);
        }
    }

    private void checkRTRoot(String absPath)
    {
        if (!new File(absPath).exists()) {
            UIGlobals.dm().stopIgnoreException();
            UI.get().show(MessageType.ERROR, absPath + " is missing.\n\n"
                    + "Please reinstall " + L.product());
            ExitCode.FATAL_ERROR.exit();
        }
        checkDisk(absPath);
    }

    // SID is null for default root
    private void checkRoot(String absPath, @Nullable SID sid)
    {
        l.debug("Checking root anchor {} {}", sid, absPath);
        // This must be checked each time in case the rootAnchor is moved
        File rootAnchor = new File(absPath);
        if (rootAnchor.exists() && rootAnchor.isDirectory()) {
            checkDisk(absPath);
        } else {
            l.debug("Root anchor missing at {}", absPath);
            try{
                notifyMissingRootAnchor_(absPath, sid);
            } catch (Exception ex) {
                // We can just warn because we will retry later.
                l.warn("Error occurred while notifying missing root anchor: {}", Util.e(ex));
            }
        }
    }

    private void checkDisk(final String absPath)
    {
        if (_ignoreDiskFullErrors) return;

        File f = new File(absPath);

        /**
         * N.B. there is a large number of users using NTFS on Win7 reporting false positives. I
         * have not identified the root cause, but OpenJDK's source code showed that OpenJDK
         * makes Win32 API to get those numbers but doesn't handle error cases. If these WIN32 API
         * calls fail, the method will return 0 for all stats. I suspect this is likely the case,
         * but I have no proof nor any idea on what could cause these API calls to fail.
         */
        if (f.getUsableSpace() == 0L || f.getFreeSpace() == 0L) {
            // FIXME(AT) logging is probably not the best idea when the disk is full for real.
            // but I want these information to further investigate the above issue.
            l.warn(ObfuscatingFormatters.formatFileMessage("full disk detected at {}",
                    f)._obfuscated);
            l.info("usable: {}", f.getUsableSpace());
            l.info("free: {}", f.getFreeSpace());
            l.info("total: {}", f.getTotalSpace());

            blockingRitualCall();

            String title = "The disk is full for " + L.product();
            String message = "Click here for detail.";
            final String details = "There are no free disk space on\n" + absPath + "\n\n" +
                    "The disk may be full or missing, or the operating system may have " +
                    "encountered an error while checking for disk usage.\n\n" +
                    "If the disk is full, please free up more disk space by deleting unused " +
                    "files and then restart AeroFS.";

            if (UI.isGUI()) {
                // use a notification because it is less disruptive. However, this also increases
                // the complexity of the logic.
                UI.get().notify(MessageType.WARN, title, message,
                        () -> askUserToIgnoreDiskFullErrors(details));
            } else {
                askUserToIgnoreDiskFullErrors(details);
            }
        }
    }

    /**
     * FIXME(AT): due to how event loops works, you get interesting behaviour when the user clicks
     * on multiple notifications and opens multiple dialogs. When the user resolves the most
     * recently opened dialog, its resolution will be carried out immediately. But if the user
     * resolves an older dialog, its resolution will not be carried out until all younger dialogs
     * are resolved.
     *
     * In the end, the client will exit if the user chooses to quit on one of the dialogs, and the
     * client will ignore disk full errors iff the user chooses to ignore on all opened dialogs.
     *
     * This behaviour may not match the user's expectations, but it should be rare enough for us to
     * ignore.
     *
     * May be called from any thread and may not return.
     */
    private void askUserToIgnoreDiskFullErrors(String message)
    {
        try {
            _ignoreDiskFullErrors = !UI.get().ask(MessageType.WARN, message,
                    "Quit " + L.product(), "Ignore Until Next Restart");
            if (!_ignoreDiskFullErrors) ExitCode.SHUTDOWN_REQUESTED.exit();

            // N.B. if the user choose to ignore disk full error, we will only restart
            // the daemon once.
            restartDaemon();
        } catch (ExNoConsole e) {
            l.warn("no Console is available to ask user to handle disk full error.");
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
     * The polling thread will block when calling this method, only showing the dialogue once.
     */
    private void notifyMissingRootAnchor_(final String oldAbsPath, final @Nullable SID sid)
        throws Exception
    {
        blockingRitualCall();

        // Check if a 'move' operation was executed from the 'Preferences' menu.
        if (onPotentialRootAnchorChange_(oldAbsPath, sid)) return;

        ShouldProceed uc = new ShouldProceed();
        UI.get().exec(() -> {
            if (UI.isGUI()) {
                new DlgRootAnchorUpdater(GUI.get().sh(), oldAbsPath, sid, uc).openDialog();
            } else {
                new CLIRootAnchorUpdater(CLI.get(), oldAbsPath, sid, uc).ask();
            }
        });

        // Proceed if permitted by the user; or if we successfully changed
        // the location of the root anchor after the user manually moved the root anchor.

        uc.proceedIf(onPotentialRootAnchorChange_(oldAbsPath, sid));
        if (uc.shouldProceed()) restartDaemon();
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

        if (UIGlobals.hasShellextService()) UIGlobals.shellext().notifyRootAnchor();

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
        try {
            UIGlobals.ritual().heartbeat();
        } catch (Exception e) {
            l.warn("Rpc call failure ignored: " + Util.e(e, Exception.class));
        }
        UIGlobals.dm().stopIgnoreException();
    }

    /**
     * Restart daemon and Ritual Notifications client after receiving a response from the user
     */
    private void restartDaemon()
    {
        try {
            UIGlobals.dm().start();  // restart the daemon
        } catch (Exception e1) {
            UI.get().show(MessageType.ERROR,
                    "An error occured while starting up " + L.product() +
                            " " + ErrorMessages.e2msgDeprecated(e1));
            l.warn(Util.e(e1));
        }
    }
}

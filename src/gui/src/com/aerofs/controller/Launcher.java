/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.controller;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.analytics.AnalyticsEvents.SimpleEvents;
import com.aerofs.base.analytics.AnalyticsEvents.UpdateEvent;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.base.id.UserID;
import com.aerofs.gui.GUIUtil;
import com.aerofs.labeling.L;
import com.aerofs.lib.ChannelFactories;
import com.aerofs.lib.LibParam.PrivateDeploymentConfig;
import com.aerofs.lib.cfg.ExNotSetup;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.launch_tasks.UILaunchTasks;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.Cfg.PortType;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.sv.client.SVClient;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.logs.LogArchiver;
import com.aerofs.ui.update.PostUpdate;
import com.aerofs.ui.update.uput.UIPostUpdateTasks;
import com.google.common.base.Preconditions;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.sql.SQLException;

import static com.aerofs.lib.cfg.Cfg.absRTRoot;

public class Launcher
{
    private static final Logger l = Loggers.getLogger(Launcher.class);
    private static final InjectableFile.Factory s_factFile = new InjectableFile.Factory();

    private static ServerSocket _ss;

    public static void destroySingletonSocket()
    {
        if (_ss != null) {
            try {
                _ss.close();
            } catch (Exception e) {
                // swallow exception on purpose
                l.error("failed to cleanly close singleton socket", e);
            }
            // for launch -> setup backtracking we need to be able to recreate the socket
            _ss = null;
        }
    }

    public static boolean needsSetup() throws Exception
    {
        checkPlatformSupported();

        if (!isSetupDone()) return true;

        checkNoOtherInstanceRunning();
        return false;
    }

    /**
     * Returns whether setup has been done or not.
     * Setup is not done iff one of those conditions are met:
     *  - there is a 'su' file under rtRoot (previous setup aborted)
     *  - device.conf not found
     */
    private static boolean isSetupDone()
    {
        if (!Cfg.inited()) return false;

        return !new File(Util.join(absRTRoot(), LibParam.SETTING_UP)).exists();
    }

    /**
     * Check if the platform is supported
     * @throws ExLaunchAborted with an appropriate error message
     */
    private static void checkPlatformSupported() throws ExLaunchAborted
    {
        // Check that OS and arch are supported
        String msg = null;
        if (OSUtil.get() == null) {
            msg = "Sorry, " + L.product() + " has yet to support " + OSUtil.getOSName() + ".";
        }

        if (OSUtil.getOSArch() == null) {
            msg = "Sorry, " + L.product() + " has yet to support your computer's architecture.";
        }

        if (msg != null) {
            SVClient.logSendDefectSyncNoCfgIgnoreErrors(true, msg, null, UserID.UNKNOWN, "unknown");
            throw new ExLaunchAborted(msg);
        }

        // On OSX, check that AeroFS is not launched from the Installer, as it would cause AeroFS
        // unable to self update.
        //
        // N.B. This assumes that the .dmg is mounted to a folder with string "Installer" in its
        // name. This string is specified in the *.dmg.template file.
        //
        if (OSUtil.isOSX() && AppRoot.abs().startsWith("/Volumes/") &&
                AppRoot.abs().contains("Installer")) {
            throw new ExLaunchAborted("Please copy " + L.product() +
                    " into your Applications folder and run it from there.");
        }
    }

    private static void checkNoOtherInstanceRunning() throws IOException, ExAlreadyRunning
    {
        // make sure only one instance of the application is running
        try {
            _ss = new ServerSocket(Cfg.port(PortType.UI_SINGLETON), 0, LibParam.LOCALHOST_ADDR);
        } catch (BindException e) {
            throw new ExAlreadyRunning();
        }
    }

    public static void launch(final boolean isFirstTime) throws Exception
    {
        try {
            // verify checksums *before* launching the daemon to avoid reporting daemon launching
            // failures due to binary issues.
            if (PostUpdate.updated()) verifyChecksums();

            // SanityPoller should be executed before the daemon starts so that the users know
            // that they moved or deleted the root anchor prior to the daemon failing because
            // that folder is missing
            UIGlobals.rap().start();

            if (isFirstTime) {
                // need to bind to singleton port
                Preconditions.checkState(_ss == null);
                checkNoOtherInstanceRunning();
            } else {
                // should already be bound to singleton port
                Preconditions.checkNotNull(_ss);
                try {
                    UIGlobals.dm().start();
                } catch (ExNotSetup e) {
                    destroySingletonSocket();
                    throw e;
                }
                UIGlobals.analytics().track(SimpleEvents.SIGN_IN);
            }

            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run()
                {
                    // delete the socket so another instance can run while we're sending the event
                    destroySingletonSocket();
                    // Shutdown the scheduler.
                    UIGlobals.scheduler().shutdown();
                }
            }));

            // Re-install the shell extension if it was updated
            if (PostUpdate.updated() && OSUtil.get().isShellExtensionAvailable()) {
                try {
                    OSUtil.get().installShellExtension(true);
                } catch (Exception e) {
                    l.warn("Shell extension failed to install post-update: " + Util.e(e));
                }
            }

             // Check and install any existing updates
            UIGlobals.updater().onStartup();

            runPostUpdateTasks();

            new UILaunchTasks().runAll();

            // TODO (WW) use UILaunchTasks to run them?
            cleanNativeLogs();

            // TODO (WW) use UILaunchTasks to run them?
            startWorkerThreads();

        } catch (Exception ex) {
            SVClient.logSendDefectAsync(true, "launch failed", ex);
            if (UIGlobals.updater() != null) {
                UIGlobals.updater().onStartupFailed();
            }
            throw ex;
        }
    }

    /**
     * Verifies that all checksums match, if we're launching AeroFS after an update
     * @throws IOException
     * @throws ExLaunchAborted
     * @throws ExFormatError
     */
    private static void verifyChecksums() throws IOException, ExLaunchAborted, ExFormatError
    {
        UIGlobals.analytics().track(new UpdateEvent(Cfg.db().get(Key.LAST_VER)));

        // After an update, verify that all checksums match
        String failedFile = PostUpdate.verifyChecksum();
        if (failedFile != null) {
            String msg = L.product() + " couldn't launch because some program files are corrupted." +
                    " Please " +
                    (UI.isGUI() ? "click " + IDialogConstants.OK_LABEL : "go to " +
                            WWW.DOWNLOAD_URL) +
                    " to download and install " + L.product() + " again. " +
                    "All your data will be intact during re-installation.";

            SVClient.logSendDefectAsync(true, msg, new Exception(failedFile + " chksum failed" +
                    new File(failedFile).length()));
            UI.get().show(MessageType.ERROR, msg);

            if (UI.isGUI()) GUIUtil.launch(WWW.DOWNLOAD_URL);

            throw new ExLaunchAborted();
        }
    }

    /**
     * Run any pending post-update tasks
     */
    private static void runPostUpdateTasks() throws Exception
    {
        new UIPostUpdateTasks(Cfg.db()).run();
        if (PostUpdate.updated()) Cfg.db().set(Key.LAST_VER, Cfg.ver());
    }

    /**
     * Clean logs generated by native C libraries (CLI Native, Daemon Native, Gui Native)
     */
    private static void cleanNativeLogs()
    {
        long now = System.currentTimeMillis();
        if (now - Cfg.db().getLong(Key.LAST_LOG_CLEANING) > 1 * C.WEEK) {
            String logs[] = { "cc.log", "gc.log", "dc.log", "lj.log" };
            for (String log : logs) s_factFile.create(absRTRoot(), log).deleteOrOnExit();
            try {
                Cfg.db().set(Key.LAST_LOG_CLEANING, now);
            } catch (SQLException e) {
                l.warn("ignored: " + Util.e(e));
            }
        }
    }

    private static void startWorkerThreads()
    {
        // There is no SV in enterprise, so the archiver's gzipped logs will stick around
        // forever. Don't compress on enterprise, and let logback delete old logs
        if (!PrivateDeploymentConfig.IS_PRIVATE_DEPLOYMENT)
        {
            new LogArchiver(absRTRoot()).start();
        }

        new CommandNotificationSubscriber(
                ChannelFactories.getClientChannelFactory(),
                UIGlobals.scheduler(),
                Cfg.did())
            .start();

        new BadCredentialNotifier();

        try {
            if (Cfg.useAutoUpdate()) {
                UIGlobals.updater().start();
            }
        } catch (Exception e) {
            SVClient.logSendDefectAsync(true, "cant start autoupdate worker", e);
        }

        UIGlobals.rnc().start();
    }
}

/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.controller;

import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExFormatError;
import com.aerofs.defects.Defects;
import com.aerofs.gui.GUIUtil;
import com.aerofs.ids.UserID;
import com.aerofs.labeling.L;
import com.aerofs.lib.*;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.Cfg.PortType;
import com.aerofs.lib.cfg.ExNotSetup;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.InfoCollector;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIGlobals;
import com.aerofs.ui.launch_tasks.UILaunchTasks;
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

import static com.aerofs.base.config.ConfigurationProperties.getStringProperty;
import static com.aerofs.defects.Defects.newDefectWithLogs;
import static com.aerofs.defects.Defects.newDefectWithLogsNoCfg;
import static com.aerofs.lib.cfg.Cfg.absRTRoot;
import static com.aerofs.lib.cfg.CfgDatabase.LAST_LOG_CLEANING;
import static com.aerofs.lib.cfg.CfgDatabase.LAST_VER;

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

        return !new File(Util.join(absRTRoot(), ClientParam.SETTING_UP)).exists();
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
            newDefectWithLogsNoCfg("launcher.platform", UserID.UNKNOWN, "unknown")
                    .setMessage(msg)
                    .sendSyncIgnoreErrors();
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
            _ss = new ServerSocket(Cfg.port(PortType.UI_SINGLETON), 0, ClientParam.LOCALHOST_ADDR);
        } catch (BindException e) {
            throw new ExAlreadyRunning();
        }
    }

    public static void launch(final boolean isFirstTime) throws Exception
    {
        try {
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
            }

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                // delete the socket so another instance can run while we're sending the event
                destroySingletonSocket();
                // Shutdown the scheduler.
                UIGlobals.scheduler().shutdown();
            }));

            // Re-install the shell extension if it was updated
            if (PostUpdate.updated() && OSUtil.get().isShellExtensionAvailable()) {
                try {
                    OSUtil.get().installShellExtension(true);
                } catch (Exception e) {
                    l.warn("Shell extension failed to install post-update: ", e);
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
            newDefectWithLogs("launcher.launch")
                    .setMessage("launch failed")
                    .setException(ex)
                    .sendAsync();
            if (UIGlobals.updater() != null) {
                UIGlobals.updater().onStartupFailed();
            }
            throw ex;
        }
    }

    /**
     * Run any pending post-update tasks
     */
    private static void runPostUpdateTasks() throws Exception
    {
        new UIPostUpdateTasks(Cfg.db()).run();
        if (PostUpdate.updated()) {
            // TODO: more robust event sending?
            String ver = Cfg.ver();
            Defects.newMetric("launcher.post_update")
                    .addData("previous_version", Cfg.db().get(LAST_VER))
                    .addData("version", ver)
                    .sendAsync();
            Cfg.db().set(LAST_VER, ver);
        }
    }

    /**
     * Clean logs generated by native C libraries (CLI Native, Daemon Native, Gui Native)
     */
    private static void cleanNativeLogs()
    {
        long now = System.currentTimeMillis();
        if (now - Cfg.db().getLong(LAST_LOG_CLEANING) > 1 * C.WEEK) {
            String logs[] = { "cc.log", "gc.log", "dc.log", "lj.log" };
            for (String log : logs) s_factFile.create(absRTRoot(), log).deleteOrOnExit();
            try {
                Cfg.db().set(LAST_LOG_CLEANING, now);
            } catch (SQLException e) {
                l.warn("ignored: ", e);
            }
        }
    }

    private static void startWorkerThreads()
    {
        new CommandNotificationSubscriber(
                NioChannelFactories.getClientChannelFactory(),
                UIGlobals.scheduler(),
                Cfg.did(),
                new InfoCollector(),
                UIGlobals.dm())
            .start();

        try {
            if (Cfg.useAutoUpdate()) {
                UIGlobals.updater().start();
            }
        } catch (Exception e) {
            newDefectWithLogs("launcher.launch.start_worker_threads")
                    .setMessage("can't start autoupdate worker")
                    .setException(e)
                    .sendAsync();
        }

        UIGlobals.rnc().start();
    }
}

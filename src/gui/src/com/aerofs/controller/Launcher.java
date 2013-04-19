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
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.Param;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.Cfg.PortType;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.proto.ControllerProto.GetInitialStatusReply;
import com.aerofs.proto.ControllerProto.GetInitialStatusReply.Status;
import com.aerofs.sv.client.SVClient;
import com.aerofs.ui.IUI.MessageType;
import com.aerofs.ui.UI;
import com.aerofs.ui.logs.LogArchiver;
import com.aerofs.ui.update.PostUpdate;
import com.aerofs.ui.update.uput.UIPostUpdateTasks;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.sql.SQLException;

import static com.aerofs.lib.cfg.Cfg.absRTRoot;

class Launcher
{
    private static final Logger l = Loggers.getLogger(Launcher.class);
    private static final InjectableFile.Factory s_factFile = new InjectableFile.Factory();

    private static ServerSocket _ss;
    private final String _rtRoot;
    private final ClientSocketChannelFactory _clientChannelFactory;

    Launcher(String rtRoot, ClientSocketChannelFactory clientChannelFactory)
    {
        _rtRoot = rtRoot;
        _clientChannelFactory = clientChannelFactory;
    }

    public static void destroySingletonSocket()
    {
        if (_ss != null) {
            try {
                _ss.close();
            } catch (Exception e) {}
        }
    }

    GetInitialStatusReply getInitialStatus()
    {
        GetInitialStatusReply.Builder reply = GetInitialStatusReply.newBuilder();
        try {
            checkPlatformSupported();

            if (!isSetupDone()) {
                reply.setStatus(Status.NEEDS_SETUP);
            } else {
                checkNoOtherInstanceRunning();
                reply.setStatus(Status.READY_TO_LAUNCH);
            }
        } catch (Exception e) {
            String msg = e.getLocalizedMessage() != null ? e.getLocalizedMessage()
                    : "Sorry, an internal error happened, preventing " + L.product() + " to launch";
            reply.setStatus(Status.NOT_LAUNCHABLE);
            reply.setErrorMessage(msg);
            if (!(e instanceof ExAlreadyRunning)) {
                SVClient.logSendDefectSyncNoCfgIgnoreErrors(true, "getInitialStatus", e,
                        UserID.UNKNOWN, _rtRoot);
            }
        }

        return reply.build();
    }

    /**
     * Returns whether setup has been done or not.
     * Setup is not done iff one of those conditions are met:
     *  - there is a 'su' file under rtRoot (previous setup aborted)
     *  - device.conf not found
     */
    private boolean isSetupDone()
    {
        if (!Cfg.inited()) return false;

        return !new File(Util.join(absRTRoot(), Param.SETTING_UP)).exists();
    }

    /**
     * Check if the platform is supported
     * @throws ExLaunchAborted with an appropriate error message
     */
    private void checkPlatformSupported() throws ExLaunchAborted
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

    private void checkNoOtherInstanceRunning() throws IOException, ExAlreadyRunning
    {
        // make sure only one instance of the application is running
        try {
            _ss = new ServerSocket(Cfg.port(PortType.UI_SINGLETON), 0, Param.LOCALHOST_ADDR);
        } catch (BindException e) {
            throw new ExAlreadyRunning();
        }
    }

    void launch(final boolean isFirstTime) throws Exception
    {
        try {
            // verify checksums *before* launching the daemon to avoid reporting daemon launching
            // failures due to binary issues.
            if (PostUpdate.updated()) verifyChecksums();

            // RootAnchorPoller should be executed before the daemon starts so that the users know
            // that they moved or deleted the root anchor prior to the daemon failing because
            // that folder is missing
            UI.rap().start();

            if (isFirstTime) {
                // need to bind to singleton port
                assert _ss == null;
                checkNoOtherInstanceRunning();
            } else {
                // should already be bound to singleton port
                assert _ss != null;
                UI.dm().start();
                UI.analytics().track(SimpleEvents.SIGN_IN);
            }

            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run()
                {
                    // delete the socket so another instance can run while we're sending the event
                    Launcher.destroySingletonSocket();
                    // Shutdown the scheduler.
                    UI.scheduler().shutdown();
                    // send event on exit synchronously
                    UI.analytics().trackSync(SimpleEvents.EXIT);
                }
            }));

            // Re-install the shell extension if it was updated
            if (PostUpdate.updated()) {
                try {
                    OSUtil.get().installShellExtension(true);
                } catch (Exception e) {
                    l.warn("Shell extension failed to install post-update: " + Util.e(e));
                }
            }

             // Check and install any existing updates
            UI.updater().onStartup();

            runPostUpdateTasks();

            cleanNativeLogs();

            startWorkerThreads();

        } catch (Exception ex) {
            SVClient.logSendDefectAsync(true, "launch failed", ex);
            if (UI.updater() != null) {
                UI.updater().onStartupFailed();
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
    private void verifyChecksums() throws IOException, ExLaunchAborted, ExFormatError
    {
        UI.analytics().track(new UpdateEvent(Cfg.db().get(Key.LAST_VER)));

        // After an update, verify that all checksums match
        String failedFile = PostUpdate.verifyChecksum();
        if (failedFile != null) {
            String msg = L.product() + " couldn't launch because some program files are corrupted." +
                    " Please " +
                    (UI.isGUI() ? "click " + IDialogConstants.OK_LABEL : "go to " +
                            WWW.DOWNLOAD_URL.get()) +
                    " to download and install " + L.product() + " again. " +
                    "All your data will be intact during re-installation.";

            SVClient.logSendDefectAsync(true, msg, new Exception(failedFile + " chksum failed" +
                    new File(failedFile).length()));
            UI.get().show(MessageType.ERROR, msg);

            if (UI.isGUI()) GUIUtil.launch(WWW.DOWNLOAD_URL.get());

            throw new ExLaunchAborted();
        }
    }

    /**
     * Run any pending post-update tasks and shutdown AeroFS if needed
     * @throws Exception
     */
    private void runPostUpdateTasks() throws Exception
    {
        boolean shutdown = new UIPostUpdateTasks(Cfg.db()).run();
        if (PostUpdate.updated()) Cfg.db().set(Key.LAST_VER, Cfg.ver());
        if (shutdown) System.exit(0);
    }

    /**
     * Clean logs generated by native C libraries (CLI Native, Daemon Native, Gui Native)
     */
    private void cleanNativeLogs()
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

    private void startWorkerThreads()
    {
        if (Cfg.useArchive()) new LogArchiver(absRTRoot()).start();

        new CommandNotificationSubscriber(
                _clientChannelFactory,
                UI.scheduler(),
                Cfg.did())
            .start();

        new BadCredentialNotifier();

        try {
            if (Cfg.useAutoUpdate()) {
                UI.updater().start();
            }
        } catch (Exception e) {
            SVClient.logSendDefectAsync(true, "cant start autoupdate worker", e);
        }

        UI.rnc().start();
    }
}

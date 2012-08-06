/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.controller;

import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.sql.SQLException;
import org.apache.log4j.Logger;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.program.Program;

import com.aerofs.gui.shellext.ShellextService;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.C;
import com.aerofs.lib.Param.SV;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.Cfg.PortType;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.ex.ExAborted;
import com.aerofs.lib.ex.ExFormatError;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.spsv.SVClient;
import com.aerofs.proto.ControllerProto.GetInitialStatusReply;
import com.aerofs.proto.ControllerProto.GetInitialStatusReply.Status;
import com.aerofs.proto.Sv.PBSVEvent;
import com.aerofs.ui.UI;
import com.aerofs.ui.UIUtil;
import com.aerofs.ui.update.PostUpdate;
import com.aerofs.ui.update.uput.UIPostUpdateTasks;

class Launcher
{
    private static final Logger l = Util.l(Launcher.class);
    private static final InjectableFile.Factory s_factFile = new InjectableFile.Factory();
    private final String _rtRoot;
    static ServerSocket _ss;

    Launcher(String rtRoot)
    {
        _rtRoot = rtRoot;
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
            } else if (needsLogin()) {
                checkNoOtherInstanceRunning();
                reply.setStatus(Status.NEEDS_LOGIN);
            } else {
                checkNoOtherInstanceRunning();
                reply.setStatus(Status.READY_TO_LAUNCH);
            }
        } catch (Exception e) {
            String msg = e.getLocalizedMessage() != null ? e.getLocalizedMessage()
                    : "Sorry, an internal error happened, preventing " + S.PRODUCT + " to launch";
            reply.setStatus(Status.NOT_LAUNCHABLE);
            reply.setErrorMessage(msg);
            SVClient.logSendDefectSyncNoCfgIgnoreError(true, "getInitialStatus", e, "unknown", _rtRoot);
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

        return !new File(Util.join(Cfg.absRTRoot(), C.SETTING_UP)).exists();
    }

    private boolean needsLogin()
    {
        return (Cfg.scrypted() == null);
    }

    /**
     * Check if the platform is supported
     * @throws ExAborted with an appropriate error message
     */
    private void checkPlatformSupported() throws ExAborted
    {
        // Check that OS and arch are supported
        String msg = null;
        if (OSUtil.get() == null) {
            msg = "Sorry, " + S.PRODUCT + " has yet to support " + OSUtil.getOSName() + ".";
        }

        if (OSUtil.getOSArch() == null) {
            msg = "Sorry, " + S.PRODUCT + " has yet to support your computer's architecture.";
        }

        if (msg != null) {
            SVClient.logSendDefectSyncNoCfgIgnoreError(true, msg, null, "unknown", "unknwon");
            throw new ExAborted(msg);
        }

        // On OSX, check that AeroFS is launched from the Applications folder
        if (OSUtil.isOSX()
                && !Cfg.staging()
                && !new File(AppRoot.abs()).getAbsolutePath().startsWith("/Applications/")
                && !new File(_rtRoot, C.NO_OSX_APP_FOLDER_CHECK).exists()) {
            throw new ExAborted("Please copy the " + S.PRODUCT +
                    " program into /Applications and try again.");
        }
    }

    private void checkNoOtherInstanceRunning() throws IOException, ExAborted
    {
        // make sure only one instance of the application is running
        try {
            _ss = new ServerSocket(Cfg.port(PortType.UI_SINGLETON), 0, C.LOCALHOST_ADDR);
        } catch (BindException e) {
            throw new ExAborted(S.PRODUCT + " is already running.");
        }
    }

    void launch(final boolean isFirstTime) throws Exception
    {
        try {
            // RootAnchorWatch should be executed before the daemon starts so that the users know
            // that they moved or deleted the root anchor prior to the daemon failing because
            // that folder is missing
            UI.raw().start();

            if (!isFirstTime) {
                UI.dm().start_();
                SVClient.sendEventAsync(PBSVEvent.Type.SIGN_IN);
            }

            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run()
                {
                    // delete the socket so another instance can run while we're sending the event
                    Launcher.destroySingletonSocket();
                    // send sv event on exit
                    SVClient.sendEventSync(PBSVEvent.Type.EXIT, null);
                }
            }));

            if (PostUpdate.updated()) {
                verifyChecksums();

                // Re-install the shell extension if it was updated
                String checksum = OSUtil.get().getShellExtensionChecksum();
                if (!checksum.equals(Cfg.db().get(Key.SHELLEXT_CHECKSUM))) {
                    try {
                        OSUtil.get().installShellExtension(true);
                        Cfg.db().set(Key.SHELLEXT_CHECKSUM, checksum);
                    } catch (Exception e) {
                        l.warn("Shell extension failed to install post-update: " + Util.e(e));
                    }
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
                UI.updater().onStartupFailed(ex);
            }
            throw ex;
        }
    }

    /**
     * Verifies that all checksums match, if we're launching AeroFS after an update
     * @throws IOException
     * @throws ExAborted
     * @throws ExFormatError
     */
    private void verifyChecksums() throws IOException, ExAborted, ExFormatError
    {
        SVClient.sendEventAsync(PBSVEvent.Type.UPDATE, Cfg.db().get(Key.LAST_VER) +
                " -> " + Cfg.ver() + ", " + System.getProperty("os.name"));

        // After an update, verify that all checksums match
        String file = PostUpdate.verifyChecksum();
        if (file != null) {
            String url = SV.DOWNLOAD_LINK;
            UIUtil.logShowSendDefect(true,
                    S.PRODUCT + " couldn't launch because some program files are corrupted." +
                            " Please " +
                            (UI.isGUI() ? "click " + IDialogConstants.OK_LABEL : "go to " + url) +
                            " to " +
                            "download and install " + S.PRODUCT +
                            " again. All your data will be intact during re-installation.",
                    new Exception(file + " chksum failed. length " +
                            new File(file).length()));
            if (UI.isGUI()) Program.launch(url);

            throw new ExAborted();
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
            for (String log : logs) s_factFile.create(Cfg.absRTRoot(), log).deleteOrOnExit();
            try {
                Cfg.db().set(Key.LAST_LOG_CLEANING, now);
            } catch (SQLException e) {
                l.warn("ignored: " + Util.e(e));
            }
        }
    }

    private void startWorkerThreads()
    {
        try {
            // start shell extension first so it is available as early as possible
            ShellextService.get().start_();
        } catch (Exception e) {
            SVClient.logSendDefectAsync(true, "cant start shellext worker", e);
        }

        new CommandNotificationSubscriber(Cfg.user(), Util.join(AppRoot.abs(), C.CA_CERT)).start();

        new HeartInvitesPoller().start();

        new BadCredentialNotificationManager();

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

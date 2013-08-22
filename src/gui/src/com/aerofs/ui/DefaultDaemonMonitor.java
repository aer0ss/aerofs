/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.ui;

import com.aerofs.base.BaseParam.WWW;
import com.aerofs.base.C;
import com.aerofs.base.Loggers;
import com.aerofs.base.ex.ExTimeout;
import com.aerofs.labeling.L;
import com.aerofs.lib.AppRoot;
import com.aerofs.lib.FrequentDefectSender;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.LibParam.Daemon;
import com.aerofs.lib.LibParam.RitualNotification;
import com.aerofs.lib.S;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.Cfg.PortType;
import com.aerofs.lib.ex.ExDaemonFailedToStart;
import com.aerofs.lib.ex.ExIndexing;
import com.aerofs.lib.ex.ExUIMessage;
import com.aerofs.lib.ex.ExUpdating;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.log.LogUtil;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.swig.driver.DriverConstants;
import com.aerofs.ui.IUI.IWaiter;
import com.aerofs.ui.IUI.MessageType;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

import static com.aerofs.lib.SystemUtil.ExitCode.CORRUPTED_DB;
import static com.aerofs.lib.SystemUtil.ExitCode.DPUT_MIGRATE_AUX_ROOT_FAILED;
import static com.aerofs.lib.SystemUtil.ExitCode.FAIL_TO_LAUNCH;
import static com.aerofs.lib.SystemUtil.ExitCode.JNOTIFY_WATCH_CREATION_FAILED;
import static com.aerofs.lib.SystemUtil.ExitCode.RELOCATE_ROOT_ANCHOR;
import static com.aerofs.lib.SystemUtil.ExitCode.S3_BAD_CREDENTIALS;
import static com.aerofs.lib.SystemUtil.ExitCode.S3_JAVA_KEY_LENGTH_MAYBE_TOO_LIMITED;
import static com.aerofs.lib.SystemUtil.ExitCode.SHUTDOWN_REQUESTED;
import static com.aerofs.lib.SystemUtil.ExitCode.WINDOWS_SHUTTING_DOWN;
import static com.aerofs.lib.SystemUtil.ExitCode.getMessage;

class DefaultDaemonMonitor implements IDaemonMonitor
{
    private static final Logger l = Loggers.getLogger(DefaultDaemonMonitor.class);

    private volatile boolean _stopping;
    private boolean _firstStart = true;

    private final FrequentDefectSender _fdsDeath = new FrequentDefectSender();
    private final FrequentDefectSender _fdsHeartbeatGone = new FrequentDefectSender();
    private final FrequentDefectSender _fdsRestartFail = new FrequentDefectSender();
    private final InjectableDriver _driver = new InjectableDriver();
    private final InjectableFile.Factory _factFile = new InjectableFile.Factory();

    /**
     * Waits until daemon starts. Throw if the daemon fails to start or timeout occurs.
     *
     * @return the daemon process.
     */
    private @Nonnull Process startDaemon()
            throws ExUIMessage, IOException, ExTimeout, ExDaemonFailedToStart
    {
        String aerofsd;
        if (OSUtil.isWindows()) {
            aerofsd = _factFile.create(AppRoot.abs()).getParentFile()
                    .newChild("aerofsd.exe").getAbsolutePath();
        } else {
            aerofsd = Util.join(AppRoot.abs(), "aerofsd");
        }

        Process proc;
        try {
            proc = SystemUtil.execBackground(aerofsd, Cfg.absRTRoot());
        } catch (Exception e) {
            throw new ExDaemonFailedToStart(e);
        }

        IWaiter waiter = null;
        int retries = UIParam.DM_LAUNCH_PING_RETRIES;
        try {
            while (true) {
                // the SanityPoller can stop the daemon before a successful ping
                if (_stopping) return proc;

                throwIfDaemonExits(proc);

                switch (getDaemonState()) {
                case INDEXING:
                    // We don't want the setup dialog to wait for indexing to be done and we can
                    // safely assume that once indexing starts the daemon will become functional at
                    // some point in the future so we can safely exit the loop.
                    //
                    // The UI will be responsible for handling ExIndexing correctly.

                    // fallthrough
                case READY:
                    return proc;

                case UPDATING:
                    if (waiter == null) {
                        waiter = UI.get().showWait("Update",
                                "Please wait while " + L.product() + " is updating...");
                    }
                    break;

                case NO_REPLY:
                    if (--retries == 0) {
                        l.error("daemon launch timed out");
                        throw new ExTimeout();
                    }
                }

                l.info("sleep for {} ms", UIParam.DAEMON_CONNECTION_RETRY_INTERVAL);
                ThreadUtil.sleepUninterruptable(UIParam.DAEMON_CONNECTION_RETRY_INTERVAL);
            }
        } finally {
            if (waiter != null) waiter.done();
        }
    }

    /**
     * Silently return if the specified daemon process is still running, or exits because Windows is
     * shutting down. Otherwise throw appropriate error messages.
     */
    private void throwIfDaemonExits(Process proc)
            throws ExUIMessage, IOException
    {
        int exitCode;
        try {
            exitCode = proc.exitValue();
        } catch (IllegalThreadStateException e) {
            // the process is still running
            return;
        }

        // TODO (WW) merge the following code into onDaemonDeath, and instead of
        // relying on the caller to show an error message, onDaemonDeath does it?
        if (exitCode == S3_BAD_CREDENTIALS.getNumber()) {
            throw new ExUIMessage(
                    "The S3 credentials were incorrect. Please check that you have" +
                    " the correct bucket name and AWS access and secret key.");
        } else if (exitCode == S3_JAVA_KEY_LENGTH_MAYBE_TOO_LIMITED.getNumber()) {
            throw new ExUIMessage(
                    L.product() + " couldn't launch due to issues with your " +
                    S.S3_ENCRYPTION_PASSWORD + ". If your Java runtime" +
                    " is provided by Oracle with limited" +
                    " crypto key strength, please download the Unlimited Strength" +
                    " Jurisdiction Policy Files at http://bit.ly/UlsKO6. " +
                    L.product() + " requires full strength AES-256 for a better margin" +
                    " of safety. Contact us at " + WWW.SUPPORT_EMAIL_ADDRESS +
                    " for more questions.");
        } else if (OSUtil.isWindows() && exitCode == WINDOWS_SHUTTING_DOWN) {
            /*
            We get this exit code on Windows in the following situation:
                1. Windows kills the daemon because the system is shutting down
                2. onDaemonDeath gets called with a normal exit code and tries to
                   restart the daemon
                3. Windows block the new process creation and returns this special
                   exit code.

                So we only get this exit code here, never in onDaemonDeath()
            */
            l.warn("Ignoring exit code " + WINDOWS_SHUTTING_DOWN
                    + " - Windows is shutting down");

        } else if (exitCode == DPUT_MIGRATE_AUX_ROOT_FAILED.getNumber()) {
            /*
               This exit code may happen when we try to run the DPUTMigrateAuxRoot task
               Therefore, it can only happen here, not in onDaemonDeath()
             */
            // TODO: legacy migration code, should be removed when we think all users
            // have updated past the DPUT in question (or decide not to support upgrade
            // from such fossilized clients)
            throw new ExUIMessage(L.product() + " couldn't launch because it couldn't " +
                    "write to: \"" + Cfg.absDefaultAuxRoot() + "\"\n\nPlease make sure that " +
                    L.product() + " has the appropriate permissions to write to that " +
                    "folder.");
        } else if (exitCode == JNOTIFY_WATCH_CREATION_FAILED.getNumber()) {
            // TODO: multiroot support (diagnose which root is failing)
            throw new ExUIMessage(L.product() + " couldn't launch because it couldn't "
                    + "watch for file changes under \"" + Cfg.absDefaultRootAnchor() + "\"\n\n"
                    + "Please make sure that " + L.product() + " has the appropriate permissions to"
                    + " access that folder.");
        } else if (exitCode == CORRUPTED_DB.getNumber()) {
            // TODO: use custom dialog to streamline reinstall process
            // w/ unlink, seed file gen if possible, ...
            throw new ExUIMessage(L.product() + " couldn't launch because of a corrupted " +
                    "database. Please delete \"" + Cfg.absRTRoot() + "\" and reinstall");
        } else {
            throw new IOException(getMessage(exitCode));
        }
    }

    enum DaemonState
    {
        READY,
        INDEXING,
        UPDATING,
        NO_REPLY
    }

    /**
     * Pings the daemon using a Ritual heartbeat and infers the current state of the daemon
     * based on the reply (or lack thereof)
     */
    private DaemonState getDaemonState()
    {
        try {
            // ritual.heartbeat() will throw immediately if it can't connect to the daemon
            UIGlobals.ritual().heartbeat();
            l.info("daemon is ready");
            return DaemonState.READY;
        } catch (ExIndexing e) {
            // On the first launch, the daemon needs to do a first full scan to make sure all
            // shared folders already present in the root anchor can be properly re-joined
            // This first scan might take a while and Ritual will throw ExIndexing on all calls
            // until it is completed so we ignore these exceptions and do not touch the retry
            // counter
            l.info("daemon indexing...");
            return DaemonState.INDEXING;
        } catch (ExUpdating e) {
            l.info("daemon updating...");
            return DaemonState.UPDATING;
        } catch (Exception e) {
            l.info("pinging daemon: " + e);
            return DaemonState.NO_REPLY;
        }
    }

    @Override
    public void start()
            throws IOException, ExUIMessage, ExDaemonFailedToStart, ExTimeout
    {
        // if we were stopped, simply let the monitor thread restart the daemon
        if (!_firstStart && _stopping) {
            _stopping = false;
            return;
        }

        // Cleanup any previous existing daemons
        try {
            kill();
        } catch (IOException e) {
            l.info("kill before start failed");
            // Ignore the exception
        }

        l.info("starting daemon");

        _stopping = false;
        final @Nonnull Process proc = startDaemon();

        if (_firstStart) {
            // start the monitor thread
            ThreadUtil.startDaemonThread("daemon-monitor", new Runnable() {
                @Override
                public void run()
                {
                    thdMonitor(proc);
                }
            });

            // Shutdown hook to ensure that the daemon is stopped when this program quits. When
            // we exit through the GUI, a daemon monitor stop is called as well. This is okay -
            // we can call stop multiple times and nothing bad will happen. This shutdown hook is
            // just here as a safety net.
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run()
                {
                    stopIgnoreException();
                }
            }));
        }

        _firstStart = false;
    }

    /**
     * Sends a defect report of the daemon's death with the exit code of the daemon's process.
     */
    private void onDaemonDeath(final int exitCode)
    {
        // Daemon is intentionally shut down to prevent inconsistencies after moving root anchor.
        // Daemon will restart in new Cfg state
        if (exitCode == RELOCATE_ROOT_ANCHOR.getNumber()) {
            return;
        } else if (exitCode == SHUTDOWN_REQUESTED.getNumber()) {
            l.warn("daemon receives shutdown request. shutdown UI now.");
            SHUTDOWN_REQUESTED.exit();
        } else if (exitCode == CORRUPTED_DB.getNumber()) {
            l.error("core db corrupted");
            UI.get().show(MessageType.ERROR, L.product() + " detected a database corruption.\n" +
                    "Please delete \"" + Cfg.absRTRoot() + "\" and reinstall.");
            CORRUPTED_DB.exit();
        }

        l.error("daemon died {}: {}", exitCode, getMessage(exitCode));

        ThreadUtil.startDaemonThread("onDaemonDeath", new Runnable() {
            @Override
            public void run()
            {
                // wait so that the daemon.log sent along the defect will
                // contain the lines logged right before the death.
                ThreadUtil.sleepUninterruptable(5 * C.SEC);
                _fdsDeath.logSendAsync("daemon died: " + getMessage(exitCode));
            }
        });
    }

    /**
     * Monitors the daemon process and restarts it if necessary. Executed in its own thread.
     */
    private void thdMonitor(@Nonnull Process proc)
    {
        //noinspection InfiniteLoopStatement
        while (true) {
            try {
                watchDaemonProcess(proc);
            } catch (IOException e) {
                l.info(Util.e(e));
            }

            // Since we returned, the daemon has died. If this was because we stopped the daemon,
            // do not restart the daemon
            if (_stopping) {
                l.info("pause a bit as we're stopping");
                while (_stopping) {
                    ThreadUtil.sleepUninterruptable(UIParam.DM_RESTART_MONITORING_INTERVAL);
                }
            }

            // Attempt to restart the daemon
            while (true) {
                try {
                    l.info("restart daemon");
                    proc = startDaemon();
                    l.info("daemon restarted");
                    break;
                } catch (ExUIMessage e) {
                    UI.get().show(MessageType.ERROR, e.getMessage());
                    FAIL_TO_LAUNCH.exit();
                } catch (Exception e) {
                    _fdsRestartFail.logSendAsync("restart daemon", e);
                }

                l.warn("restart in " + UIParam.DM_RESTART_INTERVAL);

                ThreadUtil.sleepUninterruptable(UIParam.DM_RESTART_INTERVAL);
            }
        }
    }

    /**
     * Sends a heart beat request to the daemon process and sends a log
     * if the heart beat fails.
     * FIXME: Remove this? not clear if this is ever useful
     */
    private void tryHeartBeat() {
        try {
            UIGlobals.ritual().heartbeat(Daemon.HEARTBEAT_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            _fdsHeartbeatGone.logSendAsync("daemon hb gone. " + e);
        }
    }

    private Socket connectToRitualNotification(long timeoutMs) throws IOException
    {
        final long retryInterval = RitualNotification.NOTIFICATION_SERVER_CONNECTION_RETRY_INTERVAL;
        IOException lastEx = null;

        for (long attempts = Math.max(1, timeoutMs / retryInterval); attempts > 0; attempts--) {
            try {
                return new Socket(LibParam.LOCALHOST_ADDR, Cfg.port(PortType.RITUAL_NOTIFICATION));
            } catch (IOException io) {
                l.debug("Error connecting to notification service", LogUtil.suppress(io));
                ThreadUtil.sleepUninterruptable(retryInterval);
                lastEx = io;
            }
        }

        l.warn("Unable to talk to ritual notification server; did the daemon start?");
        throw lastEx;
    }

    /**
     * Watches the daemon process and exits only when the daemon has stopped.
     *
     * @param proc The daemon process
     * @throws Exception
     */
    private void watchDaemonProcess(@Nonnull Process proc) throws IOException
    {
        Socket s = null;

        try {
            // FIXME: put this magic number in LibParam... Daemon startup timeout
            s = connectToRitualNotification(5 * C.SEC);
            s.setSoTimeout((int) Daemon.HEARTBEAT_INTERVAL);
            while (true) {
                try {
                    // read() should get nothing and block
                    if (s.getInputStream().read() == -1) {
                        l.warn("read returns -1");
                        break;
                    }
                } catch (SocketTimeoutException e) {
                    l.debug("hb test");

                    // CoreProgressWatcher should kill the daemon if it gets stuck so we don't need
                    // to do it here anymore. However, we still do regular heartbeat checks to log
                    // occurences of busy daemons
                    tryHeartBeat();
                } catch (SocketException e) {
                    l.warn("socket closed");
                    break;
                }
            }
        } finally {
            if (s != null) { s.close(); }

            if (!_stopping) {
                // Since we didn't cause the daemon to stop, find the error code and report it
                try {
                    int code = proc.waitFor();
                    onDaemonDeath(code);
                } catch (InterruptedException e) {
                    SystemUtil.fatal(e);
                }
            }
        }
    }

    @Override
    public void stopIgnoreException()
    {
        try {
            stop();
        } catch (Exception e) {
            l.warn("ignored: " + Util.e(e));
        }
    }

    @Override
    public void stop() throws IOException
    {
        _stopping = true;

        l.warn("stop daemon");

        kill();
    }

    private void kill() throws IOException
    {
        // If one of the processes failed to be killed, throw an exception
        if (_driver.killDaemon() == DriverConstants.DRIVER_FAILURE) {
            throw new IOException("failed to kill daemon process");
        }
    }
}

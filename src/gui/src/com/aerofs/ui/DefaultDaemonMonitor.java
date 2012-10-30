/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.ui;

import com.aerofs.lib.AppRoot;
import com.aerofs.lib.C;
import com.aerofs.lib.FrequentDefectSender;
import com.aerofs.lib.Param.SV;
import com.aerofs.lib.S;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.Cfg.PortType;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.ex.ExDaemonFailedToStart;
import com.aerofs.lib.ex.ExTimeout;
import com.aerofs.lib.ex.ExUIMessage;
import com.aerofs.lib.fsi.FSIClient;
import com.aerofs.lib.fsi.FSIUtil;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.ritual.RitualClient;
import com.aerofs.lib.ritual.RitualClientFactory;
import com.aerofs.lib.spsv.SVClient;
import com.aerofs.swig.driver.DriverConstants;
import com.google.common.util.concurrent.Uninterruptibles;
import org.apache.log4j.Logger;
import static com.aerofs.lib.ExitCode.*;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

class DefaultDaemonMonitor implements IDaemonMonitor
{
    private static final Logger l = Util.l(DefaultDaemonMonitor.class);

    private volatile boolean _stopping;
    private boolean _firstStart = true;

    private final FrequentDefectSender _fdsDeath = new FrequentDefectSender();
    private final FrequentDefectSender _fdsHeartbeatGone = new FrequentDefectSender();
    private final FrequentDefectSender _fdsRestartFail = new FrequentDefectSender();
    private final InjectableDriver _driver = new InjectableDriver(new CfgLocalUser());
    private final InjectableFile.Factory _factFile = new InjectableFile.Factory();

    /** waits until daemon starts and the key is set, or until timeout occurs
     *
     * @return the daemon process. N.B. The caller shouldn't use the returned value to reliably
     * detect daemon crashes as the process may be a wrapper of the daemon process.
     */
    private Process startDaemon()
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
            proc = Util.execBackground(aerofsd, Cfg.absRTRoot());
        } catch (Exception e) {
            throw new ExDaemonFailedToStart(e);
        }

        int retries = UIParam.DM_LAUNCH_PING_RETRIES;
        while (true) {
            if (proc != null) {
                try {
                    int exitCode = proc.exitValue();
                    // TODO (WW) merge the following code into onDaemonDeath, and instead of
                    // relying on the caller to show an error message, onDaemonDeath does it?
                    if (exitCode == S3_BAD_CREDENTIALS.getNumber()) {
                        throw new ExUIMessage(
                                "The S3 credentials were incorrect. Please check that you have" +
                                " the correct bucket name and AWS access and secret key.");
                    } else if (exitCode == S3_JAVA_KEY_LENGTH_MAYBE_TOO_LIMITED.getNumber()) {
                        throw new ExUIMessage(
                                S.PRODUCT + " couldn't launch due to issues with your " +
                                S.S3_ENCRYPTION_PASSWORD + ". If your Java runtime" +
                                " is provided by Oracle with limited" +
                                " crypto key strength, please download the Unlimited Strength" +
                                " Jurisdiction Policy Files at http://bit.ly/UlsKO6. " +
                                S.PRODUCT + " requires full strength AES-256 for a better margin" +
                                " of safety. Contact us at " + SV.SUPPORT_EMAIL_ADDRESS +
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
                    } else {
                        throw new IOException(getMessage(exitCode));
                    }
                } catch (IllegalThreadStateException e) {
                    // the process is still running
                }
            }

            try {
                FSIClient fsi = FSIClient.newConnection();
                try {
                    l.info("set daemon key");

                    FSIUtil.setDaemonPrivateKey_(Cfg.privateKey(), fsi);

                    l.warn("daemon started");

                    break;

                } finally {
                    fsi.close_();
                }

            } catch (Exception e) {
                l.info("pinging deamon failed: " + e);
                if (--retries == 0) {
                    l.error("pinging daemon took too long. give up");
                    throw new ExTimeout();
                }
            }

            l.info("sleep for " + UIParam.DAEMON_CONNECTION_RETRY_INTERVAL + " ms");
            Util.sleepUninterruptable(UIParam.DAEMON_CONNECTION_RETRY_INTERVAL);
        }

        return proc;
    }

    @Override
    public void start()
            throws IOException, ExUIMessage, ExDaemonFailedToStart, ExTimeout
    {
        // Cleanup any previous existing daemons
        try {
            kill();
        } catch (IOException e) {
            l.info("kill before start failed");
            // Ignore the exception
        }

        l.info("starting daemon");

        final Process proc = startDaemon();

        _stopping = false;

        if (_firstStart) {
            // start the monitor thread
            Thread thd = new Thread(new Runnable() {
                @Override
                public void run()
                {
                    thdMonitor(proc);
                }
            }, "dm");
            thd.setDaemon(true);
            thd.start();

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

        // start the log archiver
        if (Cfg.useArchive() && _firstStart) {
            Thread thd = new Thread(new Runnable() {
                @Override
                public void run()
                {
                    Util.sleepUninterruptable(UIParam.DM_LOG_ARCHIVE_STARTUP_DELAY);
                    while (true) {
                        SVClient.archiveLogsAsync();
                        Util.sleepUninterruptable(UIParam.DM_LOG_ARCHIVE_INTERVAL);
                    }

                }
            }, "dm.logArchiver");
            thd.setDaemon(true);
            thd.start();
        }

        _firstStart = false;
    }

    /**
     * Sends a defect report of the daemon's death with the exit code of the daemon's process.
     *
     * @param exitCode null if unknown
     */
    private void onDaemonDeath(final Integer exitCode)
    {
        // Daemon is intentionally shut down to prevent inconsistencies after moving root anchor.
        // Daemon will restart in new Cfg state
        if (exitCode == RELOCATE_ROOT_ANCHOR.getNumber()) {
            return;
        }

        if (exitCode == SHUTDOWN_REQUESTED.getNumber()) {
            l.warn("daemon receives shutdown request. shutdown UI now.");
            SHUTDOWN_REQUESTED.exit();
        }

        Util.startDaemonThread("onDaemonDeath", new Runnable () {
            @Override
            public void run()
            {
                // wait so that the daemon.log sent along the defect will
                // contain the lines logged right before the death.
                Util.sleepUninterruptable(5 * C.SEC);
                _fdsDeath.logSendAsync("daemon died" + (exitCode == null ? "" :
                        ": " + getMessage(exitCode)));
            }
        });
    }

    /**
     * Monitors the daemon process and restarts it if necessary. Executed in its own thread.
     *
     * @param proc May be null. See the return value of startDaemon()
     */
    private void thdMonitor(Process proc)
    {
        while (true) {
            try {
                watchDaemonProcess(proc);
            } catch (Exception e) {
                l.info(e);
            }

            // Since we returned, the daemon has died. If this was because we stopped the daemon,
            // do not restart the daemon
            if (_stopping) {
                l.info("pause a bit as we're stopping");
                Util.sleepUninterruptable(UIParam.DM_RESTART_MONITORING_INTERVAL);
                continue;
            }

            // Attempt to restart the daemon
            while (true) {
                try {
                    l.info("restart daemon");
                    proc = startDaemon();
                    l.info("daemon restarted");
                    break;
                } catch (Exception e) {
                    _fdsRestartFail.logSendAsync("restart daemon", e);
                }

                l.warn("restart in " + UIParam.DM_RESTART_INTERVAL);
                Util.sleepUninterruptable(UIParam.DM_RESTART_INTERVAL);
            }
        }
    }

    /**
     * Sends a heart beat request to the daemon process and sends a log
     * if the heart beat fails.
     *
     * @return true if the heart beat succeeded, false otherwise
     */
    private boolean tryHeartBeat() {
        RitualClient ritual = RitualClientFactory.newClient();
        try {
            Uninterruptibles.getUninterruptibly(ritual.heartbeat(),
                    UIParam.DM_HEARTBEAT_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            _fdsHeartbeatGone.logSendAsync("daemon hb gone. kill: " + e);
            return false;
        } finally {
            ritual.close();
        }
        return true;
    }

    /**
     * Watches the daemon process and exits only when the daemon has stopped.
     *
     * @param proc The daemon process
     * @throws Exception
     */
    private void watchDaemonProcess(Process proc) throws Exception
    {
        Socket s = new Socket(C.LOCALHOST_ADDR, Cfg.port(PortType.RITUAL_NOTIFICATION));
        try {
            s.setSoTimeout((int) UIParam.DM_HEARTBEAT_INTERVAL);
            while (true) {
                try {
                    // read() should get nothing and block
                    if (s.getInputStream().read() == -1) {
                        l.warn("read returns -1");
                        break;
                    }
                } catch (SocketTimeoutException e) {
                    l.info("hb test");

                    if (!tryHeartBeat()) {
                        // If the hearbeat failed, kill the daemon
                        try {
                            l.warn("hb failed. kill daemon");
                            kill();
                        } catch (Exception e2) {
                            l.warn("kill failed after hb gone: " + Util.e(e2));
                        }
                        return;
                    }
                }
            }
        } finally {
            s.close();
        }

        if (!_stopping) {
            // Since we didn't cause the daemon to stop, find the error code and report it

            if (proc != null) {
                try {
                    int code = proc.waitFor();
                    onDaemonDeath(code);
                } catch (InterruptedException e) {
                    Util.fatal(e);
                }
            } else {
                onDaemonDeath(null);
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

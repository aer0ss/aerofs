/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.ui;

import com.aerofs.base.Loggers;
import com.aerofs.lib.AppRoot;
import com.aerofs.base.C;
import com.aerofs.lib.FrequentDefectSender;
import com.aerofs.labeling.L;
import com.aerofs.lib.Param;
import com.aerofs.lib.Param.Daemon;
import com.aerofs.base.BaseParam.SV;
import com.aerofs.lib.S;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.ThreadUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.Cfg.PortType;
import com.aerofs.lib.ex.ExDaemonFailedToStart;
import com.aerofs.lib.ex.ExIndexing;
import com.aerofs.base.ex.ExTimeout;
import com.aerofs.lib.ex.ExUIMessage;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.ritual.RitualBlockingClient;
import com.aerofs.lib.ritual.RitualClient;
import com.aerofs.lib.ritual.RitualClientFactory;
import com.aerofs.swig.driver.DriverConstants;
import com.google.common.util.concurrent.Uninterruptibles;
import org.slf4j.Logger;
import static com.aerofs.lib.SystemUtil.ExitCode.*;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

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
            proc = SystemUtil.execBackground(aerofsd, Cfg.absRTRoot());
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
                                L.PRODUCT + " couldn't launch due to issues with your " +
                                S.S3_ENCRYPTION_PASSWORD + ". If your Java runtime" +
                                " is provided by Oracle with limited" +
                                " crypto key strength, please download the Unlimited Strength" +
                                " Jurisdiction Policy Files at http://bit.ly/UlsKO6. " +
                                L.PRODUCT + " requires full strength AES-256 for a better margin" +
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

                    } else if (exitCode == DPUT_MIGRATE_AUX_ROOT_FAILED.getNumber()) {
                        /*
                           This exit code may happen when we try to run the DPUTMigrateAuxRoot task
                           Therefore, it can only happen here, not in onDaemonDeath()
                         */
                        throw new ExUIMessage(L.PRODUCT + " couldn't launch because it couldn't " +
                                "write to: \"" + Cfg.absAuxRoot() + "\"\n\nPlease make sure that " +
                                L.PRODUCT + " has the appropriate permissions to write to that " +
                                "folder.");
                    } else {
                        throw new IOException(getMessage(exitCode));
                    }
                } catch (IllegalThreadStateException e) {
                    // the process is still running
                }
            }

            RitualBlockingClient ritual = RitualClientFactory.newBlockingClient();
            try {
                // Ping the daemon to see if it has started up and is listening for RPCs.
                // ritual.heartbeat() will throw immediately if it can't connect to the daemon
                l.info("hb daemon");
                ritual.heartbeat();
                l.info("daemon started");
                break;
            } catch (ExIndexing e) {
                // On the first launch, the daemon needs to do a first full scan to make sure all
                // shared folders already present in the root anchor can be properly re-joined
                // This first scan might take a while and Ritual will throw ExIndexing on all calls
                // until it is completed so we ignore these exceptions and do not touch the retry
                // counter
                l.info("daemon indexing...");
            } catch (Exception e) {
                l.info("pinging daemon failed: " + e);
                if (--retries == 0) {
                    l.error("pinging daemon took too long. giving up");
                    throw new ExTimeout();
                }
            } finally {
                ritual.close();
            }

            l.info("sleep for " + UIParam.DAEMON_CONNECTION_RETRY_INTERVAL + " ms");
            ThreadUtil.sleepUninterruptable(UIParam.DAEMON_CONNECTION_RETRY_INTERVAL);
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

        ThreadUtil.startDaemonThread("onDaemonDeath", new Runnable()
        {
            @Override
            public void run()
            {
                // wait so that the daemon.log sent along the defect will
                // contain the lines logged right before the death.
                ThreadUtil.sleepUninterruptable(5 * C.SEC);
                _fdsDeath.logSendAsync(
                        "daemon died" + (exitCode == null ? "" : ": " + getMessage(exitCode)));
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
                l.info(Util.e(e));
            }

            // Since we returned, the daemon has died. If this was because we stopped the daemon,
            // do not restart the daemon
            if (_stopping) {
                l.info("pause a bit as we're stopping");
                ThreadUtil.sleepUninterruptable(UIParam.DM_RESTART_MONITORING_INTERVAL);
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
                ThreadUtil.sleepUninterruptable(UIParam.DM_RESTART_INTERVAL);
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
                    Daemon.HEARTBEAT_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            _fdsHeartbeatGone.logSendAsync("daemon hb gone. " + e);
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
        Socket s = new Socket(Param.LOCALHOST_ADDR, Cfg.port(PortType.RITUAL_NOTIFICATION));
        try {
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
            s.close();
        }

        if (!_stopping) {
            // Since we didn't cause the daemon to stop, find the error code and report it

            if (proc != null) {
                try {
                    int code = proc.waitFor();
                    onDaemonDeath(code);
                } catch (InterruptedException e) {
                    SystemUtil.fatal(e);
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

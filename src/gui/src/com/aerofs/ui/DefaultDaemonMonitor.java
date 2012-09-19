/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.ui;

import com.aerofs.lib.AppRoot;
import com.aerofs.lib.C;
import com.aerofs.lib.FrequentDefectSender;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.Cfg.PortType;
import com.aerofs.lib.ex.ExTimeout;
import com.aerofs.lib.fsi.FSIClient;
import com.aerofs.lib.fsi.FSIUtil;
import com.aerofs.lib.injectable.InjectableDriver;
import com.aerofs.lib.ritual.RitualClient;
import com.aerofs.lib.ritual.RitualClientFactory;
import com.aerofs.lib.spsv.SVClient;
import com.aerofs.swig.driver.DriverConstants;
import com.google.common.util.concurrent.Uninterruptibles;
import org.apache.log4j.Logger;

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
    private final InjectableDriver _driver = new InjectableDriver();

    /** waits until daemon starts and the key is set, or until timeout occurs
     *
     * @return the daemon process. N.B. The caller shouldn't use the returned value to reliably
     * detect daemon crashes as the process may be a wrapper of the daemon process.
     */
    private Process startDaemon() throws Exception
    {
        Process proc = Util.execBackground(Util.join(AppRoot.abs(), "aerofsd"), Cfg.absRTRoot());

        int retries = UIParam.DM_LAUNCH_PING_RETRIES;
        while (true) {
            if (proc != null) {
                try {
                    int exit = proc.exitValue();
                    if (exit == C.EXIT_CODE_BAD_S3_CREDENTIALS) {
                        l.error("The S3 credentials were incorrect. Please check that you have" +
                                " the correct bucket name and AWS access and secret key.");
                        throw new IOException("the S3 credentials were incorrect");
                    } else if (exit == C.EXIT_CODE_BAD_S3_PASSWORD) {
                        l.error("Unable to decrypt the S3 bucket data using the supplied S3" +
                                " encryption password. Please check that you have the correct" +
                                " password.");
                        throw new IOException("the S3 encryption password did not match the" +
                                " encrypted data");
                    }

                    throw new IOException("daemon exited with code " + exit);
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
    public void start() throws Exception
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
    private void sendDefectDaemonDeath(final Integer exitCode)
    {
        // Daemon is intentionally shut down to prevent inconsistencies after moving root anchor.
        // Daemon will restart in new Cfg state
        if (exitCode == C.EXIT_CODE_RELOCATE_ROOT_ANCHOR) {
            return;
        }

        Util.startDaemonThread("sendDefectDaemonDeath", new Runnable () {
            @Override
            public void run()
            {
                // wait so that the daemon.log sent along the defect will
                // contain the lines logged right before the death.
                Util.sleepUninterruptable(5 * C.SEC);
                _fdsDeath.logSendAsync("daemon died" + (exitCode == null ? "" :
                                                                " w/ code " + exitCode));
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
            // Since we didn't cause the daemon to stop, find the error
            // code and report it

            if (proc != null) {
                try {
                    int code = proc.waitFor();
                    sendDefectDaemonDeath(code);
                } catch (InterruptedException e) {
                    Util.fatal(e);
                }
            } else {
                sendDefectDaemonDeath(null);
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

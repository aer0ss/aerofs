package com.aerofs.ui;

import com.aerofs.lib.*;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.Cfg.PortType;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.ex.ExTimeout;
import com.aerofs.lib.fsi.FSIClient;
import com.aerofs.lib.fsi.FSIUtil;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.lib.ritual.RitualClient;
import com.aerofs.lib.ritual.RitualClientFactory;
import com.aerofs.lib.spsv.SVClient;
import com.aerofs.proto.Fsi.PBFSICall;
import com.aerofs.proto.Fsi.PBFSICall.Type;
import com.aerofs.swig.driver.Driver;
import org.apache.log4j.Logger;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class DaemonMonitor {
    private static final Logger l = Util.l(DaemonMonitor.class);

    private volatile boolean _stopping;
    private boolean _firstStart = true;
    private final AtomicInteger _heartbeatPauses = new AtomicInteger();

    private final FrequentDefectSender _fdsDeath = new FrequentDefectSender();
    private final FrequentDefectSender _fdsHeartbeatGone = new FrequentDefectSender();
    private final FrequentDefectSender _fdsRestartFail = new FrequentDefectSender();
    private final InjectableFile.Factory _factFile = new InjectableFile.Factory();

    /** waits until daemon starts and the key is set, or until timeout occurs
     *
     * @return the daemon process. N.B. The caller shouldn't use the returned value to reliably
     * detect daemon crashes as the process may be a wrapper of the daemon process.
     */
    private Process startDaemon() throws Exception
    {
        assert Cfg.useDM();

        Process proc;
        if (!new File(Util.join(AppRoot.abs(), "aerofs.jar")).exists()) {
            String run = Util.join(AppRoot.abs(), "run");
            proc = Util.execBackground("bash", run, Cfg.absRTRoot(), "daemon");

        } else {
            String vmargs = Cfg.db().getNullable(Key.DAEMON_VMARGS);

            if (vmargs == null) {
                vmargs = "-ea -Xmx64m -XX:+UseConcMarkSweepGC -Djava.net.preferIPv4Stack=true";
                if (OSUtil.isOSX()) {
                    // without this flag some OSX machines run the daemon in 32bit mode :S
                    vmargs += " -d64";
                }
            }

            ArrayList<String> cmd = new ArrayList<String>();
            if (OSUtil.isOSX()) {
                OutArg<String> javaHome = new OutArg<String>();
                int retVal = Util.execForeground(javaHome, "/usr/libexec/java_home", "-d64", "-F");
                String path = javaHome.get().trim() + "/bin/java";
                if (retVal != 0 || !_factFile.create(path).exists()) {
                    throw new Exception(S.PRODUCT + " requires Java 6 to be installed");
                }
                cmd.add(path);
            } else {
                cmd.add("java");
            }
            for (String arg : vmargs.split(" ")) cmd.add(arg);

            cmd.add("-jar");
            cmd.add(Util.join(AppRoot.abs(), "aerofs.jar"));
            cmd.add(Cfg.absRTRoot());
            cmd.add("daemon");

            String[] strs = new String[cmd.size()];
            proc = Util.execBackground(cmd.toArray(strs));
        }

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
                    l.info("set deamon key");

                    // set daemon password
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

    private InjectableFile getPidFile()
    {
        return _factFile.create(Util.join(Cfg.absRTRoot(), C.PID));
    }

    /**
     * terminate the daemon process if needed
     */
    public void cleanup() throws IOException
    {
        if (!Cfg.useDM()) {
            l.info("cleanup: dm disabled");
            return;
        }

        if (getPidFile().exists()) {
            l.info("cleaning up old daemon");
            stop();
        }
    }

    // it blocks until both daemon launching and mounting succeed.
    //
    public void start_() throws Exception
    {
        if (!Cfg.useDM()) {
            l.info("start: dm disabled");
            return;
        }

        cleanup();

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
     * @param proc maybe null. see the return value of startDaemon()
     */
    private void thdMonitor(Process proc)
    {
        assert Cfg.useDM();
        while (true) thdMonitorLoop(proc);
    }

    private void thdMonitorLoop(Process proc)
    {
        Exception eHB = null;
        try {
            Socket s = new Socket(C.LOCALHOST_ADDR, Cfg.port(PortType.RITUAL_NOTIFICATION));
            try {
                s.setSoTimeout((int) UIParam.DM_HEARTBEAT_INTERVAL);
                while (true) {
                    try {
                        // read() should get nothing and block
                        if (s.getInputStream().read() == -1) {
                            throw new EOFException("read returns -1");
                        }
                    } catch (SocketTimeoutException e) {
                        if (_heartbeatPauses.get() != 0) {
                                l.warn("skip hb test");
                                continue;
                        } else {
                            l.info("hb test");
                            RitualClient ritual = RitualClientFactory.newClient();
                            try {
                                ritual.heartbeat();
                            } catch (Exception e2) {
                                eHB = e2;
                                throw e2;
                            } finally {
                                ritual.close();
                            }
                        }
                    }
                }
            } finally {
                s.close();
            }
        } catch (Exception e) {
            l.info(e);
        }

        if (_stopping) {
            l.info("pause a bit as we're stopping");
            Util.sleepUninterruptable(UIParam.DM_RESTART_MONITORING_INTERVAL);
            return;

        } else if (eHB != null) {
            _fdsHeartbeatGone.logSendAsync("daemon hb gone. kill: " + eHB);
            try {
                kill();
            } catch (Exception e) {
                // it may be normal because pid file may have been deleted
                l.warn("kill failed after hb gone: " + Util.e(e));
            }

        } else if (proc != null) {
            try {
                int code = proc.waitFor();
                sendDefectDaemonDeath(code);
            } catch (InterruptedException e) {
                Util.fatal(e);
            }
        } else {
            sendDefectDaemonDeath(null);
        }

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

    /**
     * the caller must call resumeHeartBeatTesting after it's done.
     * pauses/resumptions can be nested or interleaving
     */
    public void pauseHeartBeatTesting()
    {
        _heartbeatPauses.incrementAndGet();
    }

    public void resumeHeartBeatTesting()
    {
        assert _heartbeatPauses.getAndDecrement() > 0;
    }

    public boolean stopping()
    {
        return _stopping;
    }

    public void stopIgnoreException()
    {
        try {
            stop();
        } catch (Exception e) {
            l.warn("ignored: " + Util.e(e));
        }
    }

    /**
     * it doesn't return until the daemon has exited for sure
     */
    public void stop() throws IOException
    {
        if (!Cfg.useDM()) {
            l.info("stop: dm disabled");
            return;
        }

        _stopping = true;

        l.warn("stop daemon");

        FSIClient fsi = FSIClient.newConnection();
        try {
            try {
                fsi.send_(PBFSICall.newBuilder()
                        .setType(Type.SHUTDOWN)
                        .build());
            } catch (Exception e) {
                l.warn("stop daemon failed. kill: " + e);
                if (getPidFile().exists()) {
                    // sending SHUTDOWN failed (mostly cuz the daemon is not running)
                    kill();
                    return;
                }
            }

            long start = System.currentTimeMillis();
            long wait = 10;

            l.warn("wait for shutdown");
            while (getPidFile().exists()) {
                Util.sleepUninterruptable(wait);
                if (System.currentTimeMillis() - start > UIParam.DM_STOP_TIMEOUT) {
                    SVClient.logSendDefectAsync(true, "shutdown timed out. kill");
                    kill();
                    break;
                }
                // exponential growth
                wait = Math.min(1 * C.SEC, wait * 2);
            }

        } finally {
            fsi.close_();
        }
    }

    /**
     * it doesn't return until the daemon has exited for sure
     */
    private void kill() throws IOException
    {
        l.warn("kill daemon");

        InjectableFile fPid = getPidFile();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(fPid.getImplementation())));
        try {
            int pid = Integer.valueOf(reader.readLine());
            OSUtil.get().loadLibrary("aerofsd");
            // N.B. aerofsd logging hasn't been inited
            if (!Driver.killProcess(pid)) {
                throw new IOException("failed to kill a defunct daemon process");
            }

        } catch (NumberFormatException e) {
            // delete the offending file and ask the user to try again.
            fPid.delete();
            throw new IOException("please try again");
        } finally {
            reader.close();
        }

        // ignore error
        if (fPid.deleteIgnoreError()) l.warn("pid file not deleted");
    }
}

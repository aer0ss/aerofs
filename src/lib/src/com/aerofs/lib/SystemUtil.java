/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib;

import com.aerofs.base.Loggers;
import com.aerofs.lib.ex.ExDBCorrupted;
import com.aerofs.lib.ex.ExFatal;
import com.aerofs.rocklog.RockLog;
import com.aerofs.sv.client.SVClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

public abstract class SystemUtil
{
    private static final Logger l = LoggerFactory.getLogger(SystemUtil.class);

    /**
     * This class defines custom process exit codes and corresponding user friendly error messages.
     * By convension, these codes should be in the range of 1 to 127 inclusive.
     */
    public static enum ExitCode
    {
        ////////
        // Exit codes that indicate AeroFS program errors.
        FAIL_TO_LAUNCH("couldn't start process"),
        FATAL_ERROR("a showstopper problem"),
        FAIL_TO_INITIALIZE_LOGGING("couldn't initialize logging"),
        OUT_OF_MEMORY("out of memory"),
        JINGLE_CALL_TOO_LONG("jingle call too long"),
        JINGLE_TASK_FATAL_ERROR("jingle task fatal error"),
        JINGLE_CHANNEL_TASK_UNCAUGHT_EXCEPTION("jingle channel task had uncaught exception"),
        DPUT_MIGRATE_AUX_ROOT_FAILED("migrating the aux root failed"),
        CONFIGURATION_INIT("initializing configuration failed"),
        CORRUPTED_DB("corrupted database"),
        FAIL_TO_DETECT_ARCH("failed to detect architecture"),
        ////////

        ////////
        // Exit codes that are expected during normal operations. _All_ of them should be handled
        // manually by DaemonMonitor.
        SHUTDOWN_REQUESTED(),
        RELOCATE_ROOT_ANCHOR(),
        // Incorrect S3 access key, secret key, or bucket name for accessing bucket.
        S3_BAD_CREDENTIALS(),
        // Java may have a limited encryption key length due to export restriction. See the users of
        // this enum for more information.
        S3_JAVA_KEY_LENGTH_MAYBE_TOO_LIMITED(),
        // Failed to create a jnotify watch on the root anchor (or an external root)
        JNOTIFY_WATCH_CREATION_FAILED(),
        // Core DB was restored from a backup or otherwise tampered with
        CORE_DB_TAMPERING();
        ////////

        // Exit code when we try to relaunch the daemon while Windows is shutting down
        // Windows will abort the process creation with this code
        public static int WINDOWS_SHUTTING_DOWN = 0xC0000142;

        // The number that all the codes should be based on
        static int BASE = 66;

        private final String _message;

        ExitCode(String message)
        {
            _message = message;
        }

        /**
         * Use this ctor only for non error conditions.
         */
        ExitCode()
        {
            _message = "internal exit code";
        }

        /**
         * @return the actual number of the exit code
         */
        public int getNumber()
        {
            return BASE + ordinal();
        }

        /**
         * Exit the current process with 'this' code
         */
        public void exit()
        {
            // For some unknown reason, accessing SystemUtil.l here fails with the following error:
            //
            // java.lang.NoSuchMethodError: com.aerofs.lib.SystemUtil.access$000()Lorg/slf4j/Logger;
            // 152018.798N New I/O worker #2 @o.j.n.c.DefaultChannelPipeline,
            // An exception was thrown by a user handler while handling an exception event ([id: 0x732775ee, /127.0.0.1:55703 => /127.0.0.1:50197]
            // EXCEPTION: java.lang.NoSuchMethodError: com.aerofs.lib.SystemUtil.access$000()Lorg/slf4j/Logger;)
            Logger logger = Loggers.getLogger(SystemUtil.class);
            logger.warn("EXIT with code " + getClass().getName() + "." + this.name());
            System.exit(getNumber());
        }

        /**
         * @return a user friendly message describing the exit code. The returned string contains the
         * actual code number if it is not one of custom codes.
         */
        public static String getMessage(int exitCode)
        {
            int index = exitCode - BASE;
            if (index >= 0 && index < ExitCode.values().length) {
                return ExitCode.values()[index]._message;
            } else {
                return "exit code " + exitCode;
            }
        }
    }

    private SystemUtil()
    {
        // private to enforce uninstantiability
    }

    //
    // Fatal methods
    //

    /**
     * Send a defect report and crash the daemon. The caller can throw the returned value so that
     * the compiler wouldn't complain in certain cases.
     */
    static Error fatalWithReturn(final ExFatal e)
    {
        Throwable cause = e.getCause();

        l.error("FATAL: message:{} fatal-caller:{}", cause.getMessage(), Util.e(e));

        if (cause instanceof ExDBCorrupted) {
            ExDBCorrupted corrupted = (ExDBCorrupted) cause;
            new RockLog().newDefect("sqlite.corrupt")
                    .setMessage(corrupted._integrityCheckResult)
                    .send();
            l.error(corrupted._integrityCheckResult);
            ExitCode.CORRUPTED_DB.exit();
        } else {
            SVClient.logSendDefectSyncNoLogsIgnoreErrors(true, "FATAL:", e);
            ExitCode.FATAL_ERROR.exit();
        }

        throw e;
    }

    /**
     * Send a defect report and crash the daemon. The throws signature is to suppress compiler
     * warnings in certain cases.
     */
    public static Error fatal(Throwable e) throws Error
    {
        return fatalWithReturn(new ExFatal(e));
    }

    /**
     * See {@link com.aerofs.lib.SystemUtil#fatalWithReturn}
     */
    public static Error fatal(String message)
    {
        return fatalWithReturn(new ExFatal(message));
    }

    /**
     * Call fatal() if {@code e} is an unchecked exception. Alternatively, we can throw the same
     * {@code e} back to the caller. But unfortunately, some frameworks, noticeably netty, will not
     * crash the process.
     *
     * NOTICE: you should call this method whenever you do "catch (Exception e)" or
     * "catch (Throwable e)".
     */
    public static void fatalOnUncheckedException(Throwable e) throws Error
    {
        if (e instanceof RuntimeException || e instanceof Error) fatal(e);
    }

    //
    // Process execution
    //

    public static int execForeground(String ...cmds) throws IOException
    {
        return execForeground(null, cmds);
    }

    public static int execForegroundNoLogging(OutArg<String> output, String ... cmds)
            throws IOException
    {
        return execForeground(false, output, cmds);
    }

    public static int execForeground(OutArg<String> output, String ... cmds) throws IOException
    {
        return execForeground(true, output, cmds);
    }

    /**
     * Note: do not add double quotes around path arguments, since Java will escape spaces
     * automatically and the double quotes will interfere with the escaping.
     *
     * @return the process's exit code
     */
    private static int execForeground(boolean logging, OutArg<String> output, String ... cmds)
            throws IOException
    {
        ProcessBuilder pb = new ProcessBuilder(cmds);
        if (logging) l.debug("execForeground: " + pb.command());
        pb.redirectErrorStream(true);

        Process proc = pb.start();

        InputStream is = new BufferedInputStream(proc.getInputStream());
        StringBuilder sb = output == null ? null : new StringBuilder();
        byte[] bs = new byte[1024];
        while (true) {
            int read = is.read(bs);
            if (read == -1) break;
            String temp = new String(bs, 0, read);
            if (output != null) sb.append(temp);
            if (logging) l.debug("command '" + cmds[0] + "' output:\n" + temp);
        }

        if (output != null) output.set(sb.toString());

        try {
            return proc.waitFor();
        } catch (InterruptedException e) {
            fatal(e);
            return 0;
        }
    }

    /**
     * Note: do not add double quotes around path arguments, since Java will escape spaces
     * automatically and the double quotes will interfere with the escaping.
     */
    public static @Nonnull Process execBackground(String ... cmds) throws IOException
    {
        ProcessBuilder pb = new ProcessBuilder(cmds);
        l.debug("execBackground: " + pb.command());

        Process proc = pb.start();
        proc.getInputStream().close();
        proc.getOutputStream().close();
        proc.getErrorStream().close();
        return proc;
    }

    public static void setDefaultUncaughtExceptionHandler()
    {
        Thread.setDefaultUncaughtExceptionHandler(
                new Thread.UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(Thread t, Throwable e)
                    {
                        SVClient.logSendDefectSyncIgnoreErrors(true, "uncaught exception from "
                                + t.getName() + ". program exits now.", e);
                        // must abort the process as the abnormal thread can no longer run properly
                        fatal(e);
                    }
                }
            );
    }
}

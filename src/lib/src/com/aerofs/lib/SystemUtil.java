/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib;

import com.aerofs.sv.client.SVClient;
import org.apache.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

public abstract class SystemUtil
{
    private static final Logger l = Logger.getRootLogger();

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

        ////////
        // Exit codes that are expected during normal operations. _All_ of them should be handled
        // manually by DaemonMonitor.

        SHUTDOWN_REQUESTED(),
        RELOCATE_ROOT_ANCHOR(),

        // Incorrect S3 access key, secret key, or bucket name for accessing bucket.
        S3_BAD_CREDENTIALS(),

        // Java may have a limited encryption key length due to export restriction. See the users of
        // this enum for more information.
        S3_JAVA_KEY_LENGTH_MAYBE_TOO_LIMITED();

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
            Util.l().warn("EXIT with code " + getClass().getName() + "." + this.name());
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

    /**
     * Send a defect report and crash the daemon. Some callers need to throw the returned value
     * only to suppress compiler warnings, whereas other callers require this method to declare
     * "throws Error" for the same lame purpose.
     */
    public static Error fatal(final Throwable e) throws Error
    {
        l.fatal("FATAL:" + Util.e(e));
        SVClient.logSendDefectSyncNoLogsIgnoreErrors(true, "FATAL:", e);
        ExitCode.FATAL_ERROR.exit();
        throw new Error(e);
    }

    /**
     * See {@link com.aerofs.lib.SystemUtil#fatal(Throwable)}
     */
    public static Error fatal(String str) throws Error
    {
        return fatal(new Exception(str));
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

    public static void halt()
    {
        Integer i = new Integer(0);
        synchronized (i) { ThreadUtil.waitUninterruptable(i); }
    }

    public static Process execBackground(String ... cmds) throws IOException
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
                        SVClient.logSendDefectSyncIgnoreErrors(true,
                                "uncaught exception from " + t.getName() +
                                        ". program exits now.", e);
                        // must abort the process as the abnormal thread can
                        // no longer run properly
                        fatal(e);
                    }
                }
            );
    }
}

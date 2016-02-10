/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib;

import com.aerofs.base.Loggers;
import com.aerofs.lib.ex.ExDBCorrupted;
import com.aerofs.lib.ex.ExFatal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.UnresolvedAddressException;

import static com.aerofs.defects.Defects.*;

public abstract class SystemUtil
{
    private static final Logger l = LoggerFactory.getLogger(SystemUtil.class);

    /**
     * This class defines custom process exit codes and corresponding user friendly error messages.
     * By convention, these codes should be in the range of 1 to 127 inclusive.
     */
    public enum ExitCode
    {
        NORMAL_EXIT(0, "exited normally"),

        ////////
        // Exit codes that indicate AeroFS program errors.
        FAIL_TO_LAUNCH(66, "couldn't start process"),
        FATAL_ERROR(67, "a showstopper problem"),
        FAIL_TO_INITIALIZE_LOGGING(68, "couldn't initialize logging"),
        OUT_OF_MEMORY(69, "out of memory"),
        CONFIGURATION_INIT(74, "initializing configuration failed"),
        TOO_OLD_TO_UPGRADE(73, "client to old to upgrade"),
        CORRUPTED_DB(75, "corrupted database"),
        FAIL_TO_DETECT_ARCH(76, "failed to detect architecture"),
        ////////

        ////////
        // Exit codes that are expected during normal operations. _All_ of them should be handled
        // manually by DaemonMonitor.
        SHUTDOWN_REQUESTED(77, "manual shutdown"),
        RELOCATE_ROOT_ANCHOR(78, "root anchor relocated"),
        // Incorrect S3/Swift access key, secret key, or bucket name for accessing bucket.
        S3_BAD_CREDENTIALS(79, "bad s3 credentials"),
        SWIFT_BAD_CREDENTIALS(84, "bad swift credentials"),
        // Error in the storage cryptographic system
        REMOTE_STORAGE_INVALID_CONFIG(85, "invalid remote storage configuration"),
        // We were not able to read the Magic Chunk with the given passphrase
        STORAGE_BAD_PASSPHRASE(86, "invalid passphrase"),
        // Java may have a limited encryption key length due to export restriction. See the users of
        // this enum for more information.
        STORAGE_JAVA_KEY_LENGTH_MAYBE_TOO_LIMITED(80, "storage backend encryption key failure"),
        // Failed to create a jnotify watch on the root anchor (or an external root)
        JNOTIFY_WATCH_CREATION_FAILED(81, "jnotify watch failed"),
        // Core DB was restored from a backup or otherwise tampered with
        CORE_DB_TAMPERING(82, "db tampering detected"),
        // Failed to probe filesystem properties: assume unsuitable for syncing
        FILESYSTEM_PROBE_FAILED(83, "filesystem checks failed"),
        ////////

        // Warning: duplicate error code 83
        NEW_VERSION_AVAILABLE(83, "new version available");

        // Exit code when we try to relaunch the daemon while Windows is shutting down
        // Windows will abort the process creation with this code
        public static int WINDOWS_SHUTTING_DOWN = 0xC0000142;

        private final int _code;
        private final String _message;

        ExitCode(int code, String message)
        {
            _code = code;
            _message = message;
        }

        /**
         * @return the actual number of the exit code
         */
        public int getCode()
        {
            return _code;
        }

        /**
         * Exit the current process with 'this' code
         */
        public void exit()
        {
            exit("none");
        }

        /**
         * Exit the current process with 'this' code
         */
        public void exit(String moreInfo)
        {
            // For some unknown reason, accessing SystemUtil.l here fails with the following error:
            //
            // java.lang.NoSuchMethodError: com.aerofs.lib.SystemUtil.access$000()Lorg/slf4j/Logger;
            // 152018.798N New I/O worker #2 @o.j.n.c.DefaultChannelPipeline,
            // An exception was thrown by a user handler while handling an exception event ([id: 0x732775ee, /127.0.0.1:55703 => /127.0.0.1:50197]
            // EXCEPTION: java.lang.NoSuchMethodError: com.aerofs.lib.SystemUtil.access$000()Lorg/slf4j/Logger;)
            Logger logger = Loggers.getLogger(SystemUtil.class);
            logger.warn("EXIT with code {} more info: {}", this.name(), moreInfo);
            System.exit(getCode());
        }

        /**
         * @return a user friendly message describing the exit code.
         * The returned string always contains the given code number.
         */
        public static String getMessage(int exitCode)
        {
            for (ExitCode code : ExitCode.values()) {
                if (code.getCode() == exitCode) {
                    return String.format("%d: %s", exitCode, code._message);
                }
            }

            return "unknown exit code " + exitCode;
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
        Throwable fatalCause = e.getCause() != null ? e.getCause() : e;

        l.error("FATAL: fatal-caller:{} fatal-stack:", e.getMessage(), fatalCause);

        if (fatalCause instanceof ExDBCorrupted) {
            ExDBCorrupted corrupted = (ExDBCorrupted) fatalCause;
            newMetric("sqlite.corrupt")
                    .setMessage(corrupted._integrityCheckResult)
                    .sendAsync();
            l.error(corrupted._integrityCheckResult);
            ExitCode.CORRUPTED_DB.exit();
        } else {
            newDefect("system.fatal")
                    .setMessage("FATAL:")
                    .setException(e)
                    .sendSyncIgnoreErrors();
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
        if ((e instanceof RuntimeException && !(e instanceof UnresolvedAddressException))
                || e instanceof Error) fatal(e);
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
        l.debug("execBackground: {}", pb.command());

        Process proc = pb.start();
        proc.getInputStream().close();
        proc.getOutputStream().close();
        proc.getErrorStream().close();
        return proc;
    }

    public static void setDefaultUncaughtExceptionHandler()
    {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            newDefectWithLogs("system.uncaught_exception")
                    .setMessage("uncaught exception from " +
                            t.getName() + " . program exists now.")
                    .setException(e)
                    .sendSyncIgnoreErrors();
            // must abort the process as the abnormal thread can no longer run properly
            fatal(e);
        });
    }
}

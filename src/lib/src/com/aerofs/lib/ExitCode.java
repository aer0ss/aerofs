/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib;

/**
 * This class defines custom process exit codes and corresponding user friendly error messages.
 * By convension, these codes should be in the range of 1 to 127 inclusive.
 */
public enum ExitCode
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

    // Incorrect S3 access key or secret key for accessing bucket.
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
        if (index >= 0 && index < values().length) {
            return values()[index]._message;
        } else {
            return "exit code " + exitCode;
        }
    }
}

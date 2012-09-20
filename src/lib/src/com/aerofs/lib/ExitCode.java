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

    SHUTDOWN_REQEUSTED(),
    RELOCATE_ROOT_ANCHOR(),

    // Incorrect S3 access key or secret key for accessing bucket.
    BAD_S3_CREDENTIALS(),

    // S3 encryption password doesn't match the password used to encrypt the data stored in the
    // bucket.
    BAD_S3_DATA_ENCRYPTION_PASSWORD();

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

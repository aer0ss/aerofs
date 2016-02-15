package com.aerofs.lib;

import java.io.File;

/**
 * This class knows the root path where AeroFS binaries are installed.
 */
public class AppRoot
{
    static String _abs;

    static {
        // use class paths to figure out where AeroFS is installed. This is not ideal but the only
        // reliable way that we know of.
        // TODO (DF): once launcher is used for both the monitor and the daemon process, we can
        // switch to using much more reliable platform-specific ways to retrieve the absolute path
        // of the executable:
        // - On Linux, just new File("/proc/self/exe").getCanonicalPath()
        // - On OSX, use realpath() and _NSGetExecutablePath() (man 3 dyld)
        // - On Windows, GetModuleFileName (with NULL hModule)
        // From there, we can simply take the dirname of the executable path to find the approot.
        String cp = System.getProperty("java.class.path");
        String v = null;
        String suffix = "bin";
        for (String s : cp.split(File.pathSeparator)) {
            // First case: ./bin is on the classpath when we run development code in an
            // exploded approot.  We don't name a file separator because on Windows, Cygwin uses
            // forward slashes, but aerofsd uses backslashes.
            if (s.endsWith("bin")) {
                // Trim 'bin', but also trim the path separator
                v = s.substring(0, s.length() - (suffix.length() + 1));
                break;
            }
            // Second case: aerofs.jar is on the classpath when using a normal deployed jar
            // N.B. we have a library named ./lib/guice-aerofs.jar , which will match
            // C.AEROFS_JAR without the separator
            if (s.contains(File.separator + ClientParam.AEROFS_JAR)) {
                v = s.substring(0,s.length() - ClientParam.AEROFS_JAR.length() - 1);
                break;
            }
        }

        // v might be still null for servlets
        if (v != null) {
            set(v);
        }
    }

    /**
     * TODO Only AeroServlet uses this method. Should use an interface and possibly DI
     * in the servlet code, and then remove this method.
     */
    public static void set(String v)
    {
        File f = new File(v);
        _abs = f.getAbsolutePath();
    }

    /**
     * @return the absolute path
     */
    public static String abs()
    {
        assert _abs != null;
        return _abs;
    }
}

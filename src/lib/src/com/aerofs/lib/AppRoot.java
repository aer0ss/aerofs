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
        String cp = System.getProperty("java.class.path");
        String v;
        if (cp.endsWith(C.AEROFS_JAR)) {
            // this is a deployed version of AeroFS where all class files are bundled into the jar
            // file under AppRoot.
            v = cp.substring(0, cp.length() - C.AEROFS_JAR.length() - 1);
        } else {
            // this is an un-deployed version where all class files are saved in the bin folder
            // under AppRoot.
            v = null;
            // We use Cygwin to launch the un-deployed vesion on Windows, so forward slash rather
            // than File.separator is used here for all platforms.
            String suffix = "/bin";
            for (String path : cp.split(File.pathSeparator)) {
                if (path.endsWith(suffix)) {
                    assert v == null;   // assume only one of the paths ends with the suffix
                    v = path.substring(0, path.length() - suffix.length());
                }
            }
        }

        // v might be still null for servlets
        if (v != null) set(v);
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
        return _abs;
    }
}

package com.aerofs.lib.os;

import com.aerofs.lib.SystemUtil.ExitCode;

import java.io.FileInputStream;
import java.io.IOException;

public abstract class OSUtil
{
    public static enum OSFamily {
        WINDOWS("Windows"),
        OSX("Mac OS X"),
        LINUX("Linux");

        private String str;
        OSFamily(String str)
        {
            this.str = str;
        }

        public String getString()
        {
            return str;
        }

        @Deprecated
        @Override
        public String toString()
        {
            return getString();
        }
    }

    public static enum OSArch {
        X86,
        X86_64,
    }

    public static enum Icon
    {
        SharedFolder("sharedFolder", true),          // shared folders icon
        RootAnchor("rootFolder", false),             // root anchor icon
        WinLibraryFolder("libraryFolder", false);    // Windows-only: the library icon

        final String name;
        final boolean hasXPStyle;

        Icon(String name, boolean hasXPStyle) {
            this.name = name;
            this.hasXPStyle = hasXPStyle;
        }
    }

    private static final IOSUtil _os;
    private static final OSArch _arch;
    private static final String _version;

    static {
        String os = getOSName();

        if (os.startsWith("Windows")) {
            _os = new OSUtilWindows();
            // Execution on Windows is limited to a 32-bit JVM.
            _arch = System.getProperty("os.arch").equals("x86") ? OSArch.X86 : null;
        } else if (os.startsWith("Linux")) {
            _os = new OSUtilLinux();
            OSArch arch = null;
            try {
                // Detect the bitness of the userspace by reading the ELF header from the current
                // executable.  The first four bytes of an ELF header are "\x7fELF", the fifth is:
                // 0x01 for 32-bit binaries
                // 0x02 for 64-bit binaries

                byte[] b = readHeader();
                if (b.length != 5 || b[0] != 0x7f || b[1] != 0x45 || b[2] != 0x4c || b[3] != 0x46 ) {
                    System.err.println(
                            "Couldn't detect architecture: missing or corrupted ELF header?");
                } else {
                    switch (b[4]) {
                    case 0x01:
                        arch = OSArch.X86;
                        break;
                    case 0x02:
                        arch = OSArch.X86_64;
                        break;
                    default:
                        // No logging this early
                        System.err.println("Unknown architecture " + String.valueOf(b[4]));
                        arch = null;
                        break;
                    }
                }
            } catch (IOException e) {
                // No logging this early
                System.err.println("Couldn't detect architecture:"
                        + " unable to open/read /proc/self/exe");
                arch = null;
            }
            _arch = arch;

        } else if (os.startsWith("Mac OS X")) {
            _os = new OSUtilOSX();
            _arch = System.getProperty("os.arch").equals("x86_64") ? OSArch.X86_64 : null;

        } else {
            // should never be reached
            _os = null;
            _arch = null;
        }

        // If we didn't figure out the architecture, die loudly
        if (_arch == null) {
            ExitCode.FAIL_TO_DETECT_ARCH.exit();
        }

        _version = System.getProperty("os.version");
    }

    private static byte[] readHeader() throws IOException
    {
        FileInputStream f = null;
        try {
            byte[] b = new byte[5];
            f = new FileInputStream("/proc/self/exe");
            if (f.read(b) != 5) { return new byte[0]; }
            return b;
        } finally {
            if (f != null) f.close();
        }
    }

    private OSUtil()
    {
        // private to enforce uninstantiability
    }

    // return null if the system is not supported
    public static IOSUtil get()
    {
        return _os;
    }

    /**
     * @return null if not supported
     *
     * Use IOSUtil.getFullOSName() to retrieve more detailed OS names.
     */
    public static String getOSName()
    {
        return System.getProperty("os.name");
    }

    // return null if not supported
    public static OSArch getOSArch()
    {
        return _arch;
    }

    public static String getOSVersion()
    {
        return _version;
    }

    public static boolean isLinux()
    {
        return get().getOSFamily() == OSFamily.LINUX;
    }

    public static boolean isOSX()
    {
        return get().getOSFamily() == OSFamily.OSX;
    }

    public static boolean isOSXMountainLionOrNewer()
    {
        return isOSX() && Integer.valueOf(getOSVersion().split("\\.")[1]) >= 8;
    }

    public static boolean isOSXYosemiteOrNewer()
    {
        return isOSX() && getOSVersion().matches("10.((1[0-9]+)|([2-9][0-9]+)).?[0-9]*");
    }

    /**
     * @return true for all versions of Windows
     */
    public static boolean isWindows()
    {
        return get().getOSFamily() == OSFamily.WINDOWS;
    }

    /**
     * @return for Windows XP only
     */
    public static boolean isWindowsXP()
    {
        return isWindows() && getOSName().equals("Windows XP");
    }

    /**
     * @return true if Windows 10 - Kind of
     * SPECIAL NOTE: We currently can't distinguish between versions of windows
     * greater than 8. Once we upgrade our version of java this should work as expcted.
     * TODO: Update our version of java.
     */
    public static boolean isWindows10()
    {
        return isWindows() && getOSName().equals("Windows 10");
    }
}

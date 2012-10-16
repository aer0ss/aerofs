package com.aerofs.lib.os;

import java.io.IOException;

import com.aerofs.lib.AppRoot;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.Util;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.spsv.SVClient;

public class OSUtil
{
    public static enum OSFamily {
        WINDOWS,
        OSX,
        LINUX,
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

    static {
        String os = getOSName();
        // TODO use real dependency-injection
        InjectableFile.Factory factFile = new InjectableFile.Factory();

        if (os.startsWith("Windows")) {
            _os = new OSUtilWindows();
            // Execution on Windows is limited to a 32-bit JVM.
            _arch = System.getProperty("os.arch").equals("x86") ? OSArch.X86 : null;
        } else if (os.startsWith("Linux")) {
            _os = new OSUtilLinux(factFile);
            OSArch arch;
            try {
                OutArg<String> commandOutput = new OutArg<String>();
                // can't use Log4J as it may not be initialized yet
                // We avoid uname because we care about the userspace bitness, not the kernel
                // bitness.  /bin/ls may be a symlink to /usr/bin/ls, see
                // http://www.freedesktop.org/wiki/Software/systemd/TheCaseForTheUsrMerge
                String path = "/bin/ls";
                int result = Util.execForegroundNoLogging(commandOutput, "file", "-L", path);
                if (result != 0) {
                    Util.fatal("arch detect, error code " + result
                            + ": could not read '" + path + "'");
                }

                if (commandOutput.get().contains("64")) arch = OSArch.X86_64;
                else arch = OSArch.X86;
            } catch (IOException e) {
                arch = null;
            }
            _arch = arch;

        } else if (os.startsWith("Mac OS X")) {
            _os = new OSUtilOSX(factFile);
            _arch = System.getProperty("os.arch").equals("x86_64") ? OSArch.X86_64 : null;

        } else {
            _os = null;
            _arch = null;
        }
    }

    // return null if the system is not supported
    public static IOSUtil get()
    {
        return _os;
    }

    // return null if not supported
    public static String getOSName()
    {
        return System.getProperty("os.name");
    }

    // return null if not supported
    public static OSArch getOSArch()
    {
        return _arch;
    }

    public static boolean isLinux()
    {
        return get().getOSFamily() == OSFamily.LINUX;
    }

    public static boolean isOSX()
    {
        return get().getOSFamily() == OSFamily.OSX;
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
     * Return the path to an OS-specific icon resource
     * We need this method because those icons are not necessarily stored under approot like other
     * image resources. On Windows, they are at the top-level folder so that their path stays
     * constant across versions.
     */
    public static String getIconPath(Icon icon)
    {
        // TODO use real dependency-injection
        InjectableFile.Factory factFile = new InjectableFile.Factory();

        InjectableFile result = factFile.create(AppRoot.abs());
        if (OSUtil.isOSX())  {
            result = result.newChild("icons").newChild(icon.name + ".icns");
        } else if (OSUtil.isWindows()) {
            String suffix = icon.hasXPStyle ? (OSUtil.isWindowsXP() ? "XP" : "Vista") : "";
            result = result.getParentFile().newChild("icons").newChild(icon.name + suffix + ".ico");
        } else {
            assert false;
        }

        if (!result.exists()) {
            SVClient.logSendDefectAsync(true, "icon not found: " + result.getAbsolutePath());
        }
        return result.getAbsolutePath();
    }
}

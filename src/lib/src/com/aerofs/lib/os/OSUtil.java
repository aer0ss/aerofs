package com.aerofs.lib.os;

import java.io.IOException;

import com.aerofs.lib.OutArg;
import com.aerofs.lib.Util;
import com.aerofs.lib.injectable.InjectableFile;

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
                OutArg<String> output = new OutArg<String>();
                OutArg<String> realpath = new OutArg<String>();
                // can't use Log4J as it may not be initialized yet
                // We avoid uname because we care about the userspace bitness, not the kernel
                // bitness.  /bin/ls may be a symlink to /usr/bin/ls, see
                // http://www.freedesktop.org/wiki/Software/systemd/TheCaseForTheUsrMerge
                int result = Util.execForegroundNoLogging(realpath, "readlink", "-f", "/bin/ls");
                if (result != 0) {
                    Util.fatal("arch detect, error code " + result + ": could not read /bin/ls");
                }

                // Make sure we trim the newline at the end of the previous call's output,
                // or the 'file' command won't be able to find the returned path
                String path = realpath.get().trim();
                result = Util.execForegroundNoLogging(output, "file", path);
                if (result != 0) {
                    Util.fatal("arch detect, error code " + result
                            + ": could not read '" + path + "'");
                }

                if (output.get().contains("64")) arch = OSArch.X86_64;
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

    public static boolean isWindows()
    {
        return get().getOSFamily() == OSFamily.WINDOWS;
    }
}

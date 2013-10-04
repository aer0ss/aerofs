package com.aerofs.lib.os;

import com.aerofs.lib.AppRoot;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.sv.client.SVClient;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.apache.commons.lang.text.StrSubstitutor;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Map;

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

                /* FIXME(jP): Ok, the following comment and block of code is crazy.
                 * There is no reason we can't have the logging subsystem initialized - the only
                 * blocker here is that we do some magic in daemon to direct the log name
                 * based on the program name. We could push all of that to the launcher, and
                 * have _it_ tell us what config file to use (daemon/gui/shell and prod/staging)
                 * and then this comment disappears.
                 */

                // can't use logging subsystem as it may not be initialized yet
                // We avoid uname because we care about the userspace bitness, not the kernel
                // bitness.  /bin/ls may be a symlink to /usr/bin/ls, see
                // http://www.freedesktop.org/wiki/Software/systemd/TheCaseForTheUsrMerge
                String path = "/bin/ls";
                int result = SystemUtil.execForegroundNoLogging(commandOutput, "file", "-L", path);
                if (result != 0) {
                    SystemUtil.fatal(
                            "arch detect, error code " + result + ": could not read '" + path + "'");
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

    static final String PROPERTY_HOME = "HOME";

    /**
     * Given a path, replace the environment variables in the path with actual values.
     *
     * Supported Format:
     *   - expand ~ to the environment variable `HOME`.
     *   - expand ${variable_name} to its value.
     *
     * Note: substitution _is case sensitive_.
     *
     * @param path - the path containing environment variables.
     * @param additionalVars - additional environment variables to supplement those provided by
     *   the system. This argument is nullable, and variables defined here takes precedence over
     *   the ones provided by the system.
     * @return the resulting path with environment variables replaced with the values.
     */
    public static String replaceEnvironmentVariables(String path,
            @Nullable ImmutableMap<String, String> additionalVars)
    {
        if (path == null) return null;

        Map<String, String> env = System.getenv();

        if (additionalVars != null && additionalVars.size() > 0) {
            // N.B. System.getenv() returns an immutable map.
            env = Maps.newHashMap(env);
            env.putAll(additionalVars);
        }

        path = path.replace("~", env.get(PROPERTY_HOME));

        return StrSubstitutor.replace(path, env);
    }
}

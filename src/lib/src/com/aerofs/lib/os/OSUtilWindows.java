package com.aerofs.lib.os;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.util.regex.Pattern;

import com.aerofs.base.Loggers;
import com.aerofs.labeling.L;
import com.aerofs.lib.*;
import com.aerofs.lib.LibParam.RootAnchor;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.OSUtil.Icon;
import com.aerofs.swig.driver.Driver;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;

import javax.annotation.Nullable;

import static com.aerofs.lib.os.OSUtil.replaceEnvironmentVariables;
import static org.apache.commons.lang.StringUtils.isBlank;

public class OSUtilWindows implements IOSUtil
{
    private static final Logger l = Loggers.getLogger(OSUtilWindows.class);

    private static final String LOCAL_APP_DATA = "LOCALAPPDATA";

    @Override
    public String getDefaultRTRoot()
    {
        // Before you update this method, please take a look at ULRtrootMigration first.
        try {
            return getLocalAppDataPath() + '\\' + L.productSpaceFreeName();
        } catch (FileNotFoundException ex) {
            return "C:\\" + L.productSpaceFreeName();
        }
    }

    /**
     * On Windows Vista and later, %LocalAppData% is defined and is the canonical folder for
     *   local application data. On earlier version %LocalAppData% is not defined.
     * On Windows XP, the canonical place to put local application data is
     *   "%UserProfile%\Local Settings\Application Data".
     * Note that %UserProfile% should be defined on XP and later.
     *
     * @return path to the platform's local application data folder,
     * @throws FileNotFoundException - if we are unable to determine the platform's local application
     *   data folder.
     */
    private @Nullable String getLocalAppDataPath() throws FileNotFoundException
    {
        // Before you update this method, please take a look at ULTRtrootMigration first.
        String path = System.getenv(LOCAL_APP_DATA);
        if (!isBlank(path) && new File(path).isDirectory()) return path;

        if (OSUtil.isWindowsXP()) {
            path = System.getenv("USERPROFILE");
            if (!isBlank(path)) {
                path += "\\Local Settings\\Application Data";

                if (new File(path).isDirectory()) return path;
            }
        }

        throw new FileNotFoundException("The system's local application data folder cannot be " +
                "determined. Please set the environment variable %LOCALAPPDATA%.");
    }

    @Override
    public OSUtil.OSFamily getOSFamily()
    {
        return OSUtil.OSFamily.WINDOWS;
    }

    @Override
    public String getFullOSName()
    {
        return System.getProperty("os.name");
    }

    @Override
    public void loadLibrary(String library)
    {
        // we don't use PATH or -Djava.library.path on Windows
        System.load(Util.join(AppRoot.abs(), library + ".dll"));
    }

    /**
     * N.B. we've added support to load default root anchor parent from configuration
     *   properties. The properties value will be preferred over the default
     *   platform-specific policy.
     *
     * In addition, we support macro expansion of the form: ${environment_variable}. The macro
     *   expansion _is case sensitive_ and _cannot_ be nested, this can be changed if necessary.
     *
     * @return usually the path to My Documents
     */
    @Override
    public String getDefaultRootAnchorParent()
    {
        Optional<String> value = RootAnchor.DEFAULT_LOCATION_WINDOWS;
        if (value.isPresent()) {
            ImmutableMap<String, String> env;

            try {
                env = ImmutableMap.of(LOCAL_APP_DATA, getLocalAppDataPath());
            } catch (FileNotFoundException e) {
                env = null;
            }

            return replaceEnvironmentVariables(value.get(), env);
        }

        try {
            OutArg<String> out = new OutArg<String>();
            int exit = SystemUtil.execForeground(out, "reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Shell Folders",
                    "/v", "Personal");
            if (exit != 0) throw new IOException("exit code " + exit + ": " + out.get());

            BufferedReader reader = new BufferedReader(new StringReader(out.get()));
            while (true) {
                String line = reader.readLine();
                if (line == null) throw new IOException("Personal line not found");
                if (line.contains("Personal")) {
                    int pos = line.indexOf("REG_SZ");
                    if (pos < 0) throw new IOException("REG_SZ not found");
                    String path = line.substring(pos + "REG_SZ".length()).trim();
                    File f = new File(path);
                    if (!f.exists()) throw new IOException(f + " doesn't exist");
                    return path;
                }
            }

        } catch (IOException e) {
            l.warn(Util.e(e));
            return getUserHomeDir();
        }
    }

    @Override
    public void addToFavorite(String path) throws IOException
    {
        if (!OSUtil.isWindowsXP()) {
            SystemUtil.execBackground(AppRoot.abs() + File.separator + "shortcut.exe",
                    "/F:" + getUserHomeDir() + File.separator + "Links"
                            + File.separator + "AeroFS.lnk",
                    "/A:C",
                    "/I:" + OSUtil.getIconPath(Icon.WinLibraryFolder),
                    "/T:" + path);
        }
    }

    @Override
    public void removeFromFavorite(String path) throws IOException
    {
        if (!OSUtil.isWindowsXP()) {
            File f = new File(getUserHomeDir() + File.separator +
                    "Links" + File.separator + "AeroFS.lnk");
            f.delete();
        }
    }

    @Override
    public String getUserHomeDir()
    {
        return System.getenv("USERPROFILE");
    }

    final static private Pattern RESERVED_FILENAME_PATTERN;
    final static private Pattern INVALID_FILENAME_CHARS;

    static {
        // "I know, I'll use regexes!" Well, now you have two problems.
        // Note the use of groups; this pattern matches:
        // - a filename made up entirely of "."s (the first group),
        // OR
        //   - one of the Illegal Filenames,
        //   or one of the Illegal Filenames with a suffix.
        // The ? on the final group makes the suffix optional (zero or one of these)
        // and does not apply to the first, ".+" group.
        RESERVED_FILENAME_PATTERN = Pattern.compile(
                "(\\.+)|((CON|CLOCK\\$|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?)",
                Pattern.CASE_INSENSITIVE);

        // This regex is simpler, just checking for certain characters that are forbidden.
        INVALID_FILENAME_CHARS = Pattern.compile("[/\\\\:*?\"<>|]");
    }

    /**
     * Check if this filename has a chance at being valid on Windows.
     * Specific failures checked for are: illegal characters for filenames
     * on Windows, as well as reserved filenames (COM1, NUL, PRN...)
     * or a filename made up of dots.
     *
     * See http://msdn.microsoft.com/en-us/library/windows/desktop/aa365247(v=vs.85).aspx
     *
     * The caller should throw ExInvalidCharacter if an exception is needed.
     */
    public static boolean isValidFileName(String name)
    {
        return !(INVALID_FILENAME_CHARS.matcher(name).find()
                 || RESERVED_FILENAME_PATTERN.matcher(name).matches());
    }

    /**
     * If the input name is not a valid file name, return a valid name derived from it
     *
     * 1. append a " - reserved" suffix to reserved names (old MSDOS relics)
     * 2. replace any invalid characters by an underscore
     */
    public static String cleanName(String name)
    {
        if (RESERVED_FILENAME_PATTERN.matcher(name).matches()) return name + " - reserved";
        return INVALID_FILENAME_CHARS.matcher(name).replaceAll("_");
    }

    @Override
    public String getFileSystemType(String path, OutArg<Boolean> remote)
        throws IOException
    {
        OSUtil.get().loadLibrary("aerofsd");

        byte[] buf = new byte[256];
        int res = Driver.getFileSystemType(null, path, buf, buf.length);
        if (res < 0) {
            throw new IOException("can't get fs type: " + path);
        } else {
            remote.set(res == Driver.FS_REMOTE);
        }
        return Util.cstring2string(buf, true);
    }

    /**
     * @param remote null if unknown, as set by getFileSystemType
     */
    @Override
    public boolean isFileSystemTypeSupported(String type, Boolean remote)
    {
        // see http://cygwin.com/ml/cygwin/2005-11/msg00850.html
        assert remote != null;
        // Allow all local filesystems, and no remote ones
        return !remote;
    }

    /**
     * N.B. this method has false negatives: all remote paths are considered
     * on different drives
     */
    @Override
    public boolean isInSameFileSystem(String p1, String p2) throws IOException
    {
        char d1 = new File(p1).getCanonicalPath().charAt(0);
        char d2 = new File(p2).getCanonicalPath().charAt(0);
        if (d1 == '\\' || d2 == '\\') return false;
        else return Character.toUpperCase(d1) == Character.toUpperCase(d2);
    }

    @Override
    public String getShellExtensionName()
    {
        return "Shell Extension";
    }

    @Override
    public boolean isShellExtensionAvailable()
    {
        // Shell extensions are installed by the installer and patcher on Windows
        return false;
    }

    @Override
    public boolean isShellExtensionInstalled()
    {
        return false;
    }

    @Override
    public void installShellExtension(boolean silently)
    {
        // Shell extensions are installed by the installer and patcher on Windows
    }

    @Override
    public void startShellExtension(int port)
    {
        // Shell extensions are installed by the installer and patcher on Windows
    }

    @Override
    public void markHiddenSystemFile(String absPath) throws IOException
    {
        File f = new File(absPath);

        // attrib doesn't return error even if the file doesn't exist
        if (!f.exists()) throw new IOException("file not found");

        // TODO use JNI + SetFileAttributes() instead of calling attrib (or wait until Java 7)
        SystemUtil.execForeground("attrib", "+S", "+H", f.getAbsolutePath());
    }

    @Override
    public void copyRecursively(InjectableFile source, InjectableFile destination, boolean exclusive,
            boolean keepMTime)
            throws IOException
    {
        // if directory is changed to a different file midway list will return null,
        // avoids nullpointer exception when iterating over children
        String[] children = source.list();
        if (children != null) {
            if (!destination.mkdirIgnoreError() && exclusive) {
                // mkdir may also fail if it already exists
                throw new IOException("cannot create " + destination.getPath() +
                        ". it might already exist");
            }

            for (int i = 0; i < children.length; i++) {
                InjectableFile fChildFrom = source.newChild(children[i]);
                InjectableFile fChildTo = destination.newChild(children[i]);
                fChildFrom.copyRecursively(fChildTo, exclusive, keepMTime);
            }
        } else {
            source.copy(destination, exclusive, keepMTime);
        }
    }

    @Override
    public void showInFolder(String path)
    {
        /*
        Work around Java bug 6511002 (http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6511002)

        Java automatically adds quotes around arguments if they contain a space and don't start
        with a quote. So this argument:
        foo:"c:\some path"
        gets incorrectly re-quoted to:
        "foo:"c:\some path""
        even though the additional quotes are not necessary and actually break the argument.

        To work around this issue, we split the path on spaces, so Java 'sees' it as a series of
        arguments that don't contain spaces, and therefore don't try to add any quotes.

        Another possible work around would be to create a .bat file in a temp location that
        actually runs the command that we want to perform.

        NB: This is the command that we want to run:
        explorer.exe /select,"C:\path to file\"
        */
        String[] arr = path.split(" ");
        arr[0] = "/select,\"" + arr[0];
        arr[arr.length - 1] += '"';

        String[] arr2 = new String[arr.length + 1];
        arr2[0] = "explorer.exe";
        System.arraycopy(arr, 0, arr2, 1, arr.length);

        try {
            SystemUtil.execBackground(arr2);
        } catch (IOException e) {
            l.warn("showInFolder failed: " + Util.e(e));
        }
    }
}

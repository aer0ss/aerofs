package com.aerofs.lib.os;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;

import com.aerofs.lib.*;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.OSUtil.Icon;
import com.aerofs.swig.driver.Driver;
import org.apache.log4j.Logger;

public class OSUtilWindows implements IOSUtil
{
    private static final Logger l = Util.l(OSUtilWindows.class);

    @Override
    public String getDefaultRTRoot()
    {
        String name;
        if (Cfg.staging()) {
                name = L.PRODUCT + ".staging";
        } else {
                name = L.PRODUCT;
        }

        String path = System.getenv("APPDATA");
        return (path == null ? "C:" : path) + "\\" + name;
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
     * @return usually the path to My Documents
     */
    @Override
    public String getDefaultRootAnchorParent()
    {
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
            return System.getProperty("user.home");
        }
    }

    @Override
    public void addToFavorite(String path) throws IOException
    {
        if (!OSUtil.isWindowsXP()) {
            SystemUtil.execBackground(AppRoot.abs() + File.separator + "shortcut.exe",
                    "/F:\"" + System.getProperty("user.home") + File.separator +
                    "Links" + File.separator + "AeroFS.lnk" + "\"",
                    "/A:C",
                    "/I:\"" + OSUtil.getIconPath(Icon.WinLibraryFolder) + "\"",
                    "/T:\"" + path + "\"");
        }
    }

    @Override
    public void remFromFavorite(String path) throws IOException
    {
        if (!OSUtil.isWindowsXP()) {
            File f = new File(System.getProperty("user.home") + File.separator +
                    "Links" + File.separator + "AeroFS.lnk");
            f.delete();
        }
    }

    final static private HashSet<Character> INVALID_FILENAME_CHARS;

    static {
        char[] cs = { '/', '\\', ':', '*', '?', '"', '<', '>', '|' };
        INVALID_FILENAME_CHARS = new HashSet<Character>(cs.length);
        for (char c : cs) { INVALID_FILENAME_CHARS.add(c); }
    }

    // the caller should throw ExInvalidCharacter if exception is needed
    public static boolean isValidFileName(String name)
    {
        for (int i = 0; i < name.length(); i++) {
            if (INVALID_FILENAME_CHARS.contains(name.charAt(i))) return false;
        }

        return true;
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
        if (!remote) {
            return true;
        } else if (type.equals("NTFS")) {
            return true;
        } else {
            return false;
        }
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
    public String getShellExtensionChecksum()
    {
        return "";
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

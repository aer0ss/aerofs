
package com.aerofs.lib.os;

import com.aerofs.base.BaseUtil;
import com.aerofs.labeling.L;
import com.aerofs.lib.*;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.lib.os.OSUtil.Icon;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.io.Files;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OSUtilLinux extends AbstractOSUtilLinuxOSX
{
    private String _fullOSName;

    public OSUtilLinux()
    {
        super();
    }

    @Override
    public final String getDefaultRTRoot()
    {
        return System.getenv("HOME") + File.separator + "." + L.productUnixName();
    }

    @Override
    public String getDefaultRootAnchorParent()
    {
        return getDefaultRootAnchorParentImpl(ClientParam.RootAnchor.DEFAULT_LOCATION_LINUX);
    }

    @Override
    public OSUtil.OSFamily getOSFamily()
    {
        return OSUtil.OSFamily.LINUX;
    }

    @Override
    public String getFullOSName()
    {
        if (_fullOSName == null) {

            // Try to read the distro name from some known places
            String result = readFirstFile("/etc", Pattern.compile(".*release$"));
            if (result.isEmpty()) result = readFirstFile("/etc", Pattern.compile("^issue$"));
            if (result.isEmpty()) result = readFirstFile("/etc", Pattern.compile(".*version$"));

            _fullOSName = parseDistroName(result);
        }

        return _fullOSName;
    }

    /**
     * Helper function to parse a human-readable name out of the distro information that we got
     * from parsing a couple of well-known files
     * @return Some nice string, or "Linux" in the worst case
     */
    static String parseDistroName(final String distroInfo)
    {
        // Try to get the distro name from a couple of known properties
        String distroName = parseProperty("PRETTY_NAME", distroInfo);
        if (distroName.isEmpty()) distroName = parseProperty("DISTRIB_DESCRIPTION", distroInfo);
        if (distroName.isEmpty()) distroName = parseProperty("NAME", distroInfo);
        if (distroName.isEmpty()) distroName = parseProperty("DISTRIB_ID", distroInfo);

        // If all that fails, use the first non-empty, non-comment, non-starting with "LSB_VERSION"
        // line. (Because the LSB_VERSION field is full of useless information)
        if (distroName.isEmpty()) {
            for (String aLine : distroInfo.split("\n")) {
                String line = aLine.trim();
                if (!line.isEmpty() && !line.startsWith("LSB_VERSION") && !line.startsWith("#")) {
                    distroName = line;
                    break;
                }
            }
        }

        // Remove /etc/issue's format characters
        distroName = distroName.replaceAll("\\\\[a-zA-Z]", "");

        // Remove empty parenthesis (which is a result of removing format characters above)
        distroName = distroName.replace("()", "");

        // Ensure that there is at least one alphabetical character. (We had cases where the name
        // was just a release date - only numbers)
        Matcher letters = Pattern.compile("[a-zA-Z]").matcher(distroName);
        if (!letters.find()) {
            distroName = "";
        }

        // Trim and ensure a reasonable length
        distroName = distroName.trim();
        if (distroName.length() > 100) {
            distroName = distroName.substring(0, 100);
        }

        return !distroName.isEmpty() ? distroName : "Linux";
    }

    private static String parseProperty(final String propertyName, final String distroName)
    {
        /*
            Regexp explanation:
            Match the property name, followed by '=', followed by an optional double-quote, then
            capture non-greedily all characters, and stop as soon as a double-quote or a newline is
            found.
         */
        Matcher m = Pattern.compile(propertyName + "=\"?(.*?)[\"\\n]").matcher(distroName);
        return m.find() ? m.group(1) : "";
    }

    /**
     * Helper method for getFullOSName()
     * Returns the content of the first file in 'dir' matching 'pattern', or an empty string if
     * no file is found.
     * Note: extra whitespaced is trimmed from the return value
     */
    private String readFirstFile(String dir, final Pattern pattern)
    {
        File[] files = new File(dir).listFiles((arg0, name) -> {
            return pattern.matcher(name).matches();
        });

        if (files == null || files.length == 0) return "";

        try {
            return Files.toString(files[0], Charsets.UTF_8).trim();
        } catch (IOException e) {
            l.warn("trying to read {}", files[0], e);
            return "";
        }
    }

    @Override
    public void addToFavorite(String path) throws IOException
    {
        File f = new File(System.getenv("HOME") + File.separator + ".gtk-bookmarks");

        if (f.exists()) {
            try (FileWriter fw = new FileWriter(f, true)) {
                fw.write("file://" + path);
            }
        }
    }

    @Override
    public void removeFromFavorite(String path) throws IOException
    {
        InjectableFile.Factory factFile = new InjectableFile.Factory();
        InjectableFile f = factFile.create(Util.join(System.getenv("HOME"), ".gtk-bookmarks"));
        InjectableFile tmpFile = factFile.createTempFile(".gtk-bookmarks","$$$");

        String currentLine;
        try (BufferedReader in = new BufferedReader(new FileReader(f.getImplementation()))) {
            try (BufferedWriter out = new BufferedWriter(new FileWriter(tmpFile.getImplementation()))) {
                while ((currentLine = in.readLine()) != null) {
                    if (currentLine.contains(path)) continue;
                    out.write(currentLine);
                }
            }
            tmpFile.copy(f, false, false);
            tmpFile.deleteOrOnExit();
        }
    }

    @Override
    public String getFileSystemType(String path, OutArg<Boolean> remote) throws IOException
    {
        OutArg<String> output = new OutArg<>();
        SystemUtil.execForeground(output, "stat", "-f", "--format=%T", path);
        String fstype = output.get().trim();
        remote.set(fstype.equals("nfs"));
        return fstype;
    }

    @Override
    public boolean isFileSystemTypeSupported(String type, Boolean remote)
    {
        String[] fss = new String[] { "EXT", "BTRFS", "ECRYPTFS", "VZFS",
                "REISER", "XFS", "UFS", "CRYPT", "JFS", "SIMFS", "ZFS",
                // VZFS when stat doesn't know about that magic number.
                "UNKNOWN (0X565A4653)",
                // ZFS when stat doesn't know about that magic number. This is added to support
                // Lumos Labs's ancient Ubuntu systems. Below is Drew's comment:
                // https://github.com/zfsonlinux/zfs/blob/master/include/sys/zfs_vfsops.h#L95
                // suggests that the best-known ZFS-on-Linux patchset uses this number.
                //
                // http://osdir.com/ml/bug-coreutils-gnu/2012-08/msg00111.html also
                // suggests that this is sufficiently canonical that it's fixed in newer
                // versions of coreutils, but Ubuntu (whatever the heck Lumos is running)
                // is unlikely to receive them.
                "UNKNOWN (0X2FC12FC1)",
        };
        for (String fs : fss) if (type.toUpperCase().startsWith(fs.toUpperCase())) return true;
        return false;
    }

    @Override
    public String getShellExtensionName()
    {
        return "Shell Extension";
    }

    @Override
    public boolean isShellExtensionAvailable()
    {
        return false; // No shell extension on Linux
    }

    @Override
    public boolean isShellExtensionInstalled()
    {
        return false;
    }

    @Override
    public void installShellExtension(boolean silently)
    {
        // Shell extensions not yet implemented on Linux
    }

    @Override
    public void startShellExtension(File shellExtSocketFile)
    {
        // Shell extensions not yet implemented on Linux
    }

    @Override
    public void showInFolder(String path)
    {
        try {
            // nautilus is the default file manager for Gnome
            // dolphin is the default file manager for KDE
            //
            // We won't officially support others for now, but users can override the file browser
            // by exporting AEROFS_FILE_BROWSER in their environment to be the absolute path to an
            // executable that accepts one argument: the path to the file to be selected.
            String userSpecifiedFileBrowser = System.getenv("AEROFS_FILE_BROWSER");
            if (userSpecifiedFileBrowser != null) {
                SystemUtil.execBackground(userSpecifiedFileBrowser, path);
            } else {
                // Try to pick a decent filebrowser by default
                String kdeFullSession = System.getenv("KDE_FULL_SESSION");
                if (kdeFullSession != null && kdeFullSession.equals("true")) {
                    SystemUtil.execBackground("dolphin", "--select", path);
                } else {
                    // technically you're supposed to detect GNOME by seeing if
                    // org.gnome.SessionManager exists on the session bus, but we'll just
                    // assume GNOME if not KDE for now, which won't be a regression from assuming
                    // GNOME all the time
                    SystemUtil.execBackground("nautilus", path);
                }
            }
        } catch (IOException e) {
            l.warn("showInFolder failed: ", e);
        }
    }

    final static private Pattern INVALID_FILENAME_CHARS = Pattern.compile("[/\0]");

    @Override
    public String cleanFileName(String name)
    {
        return BaseUtil.truncateIfLongerThan(INVALID_FILENAME_CHARS.matcher(name).replaceAll("_"),
                255);
    }

    @Override
    public String normalizeInputFilename(String name) { return name; }

    @Override
    public boolean isInvalidFileName(String name)
    {
        return name.length() > 255 || INVALID_FILENAME_CHARS.matcher(name).find();
    }

    @Override
    public String reasonForInvalidFilename(String name)
    {
        if (name.length() > 255) {
            return S.INVALID_TOO_LONG;
        } else if (INVALID_FILENAME_CHARS.matcher(name).find()) {
            return S.INVALID_FORBIDDEN_CHARACTERS;
        }
        return null;
    }


    @Override
    public String getIconPath(Icon icon)
    {
        // No icon path on Linux??? :(
        Preconditions.checkState(false);
        return "";
    }
}


package com.aerofs.lib.os;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.aerofs.labeling.L;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.injectable.InjectableFile;
import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class OSUtilLinux extends AbstractOSUtilLinuxOSX
{
    private String _fullOSName;

    public OSUtilLinux(InjectableFile.Factory factFile)
    {
        super(factFile);
    }

    @Override
    public final String getDefaultRTRoot()
    {
        String name;
        if (Cfg.staging()) {
                name = L.get().productUnixName() + ".staging";
        } else {
                name = L.get().productUnixName();
        }

        return System.getenv("HOME") + File.separator + "." + name;
    }

    @Override
    public OSUtil.OSFamily getOSFamily()
    {
        return OSUtil.OSFamily.LINUX;
    }

    @Override
    public String getFullOSName()
    {
        if (_fullOSName != null) return _fullOSName;

        // Try to read the distro name from some known places
        String result = readFirstFile("/etc", Pattern.compile(".*release$"));
        if (result.isEmpty()) result = readFirstFile("/etc", Pattern.compile("^issue$"));
        if (result.isEmpty()) result = readFirstFile("/etc", Pattern.compile(".*version$"));

        // TODO (GS): Keeping this log line for a while to see what sort of distro strings we get
        // Remove it after Dec 2012
        l.info("Linux distro name: " + result);

        // Parse os-release's PRETTY_NAME field
        Matcher m = Pattern.compile("PRETTY_NAME=\"?([^\"]*)\"?$").matcher(result);
        if (m.find()) result = m.group(1);

        // Parse lsb-release's DISTRIB_DESCRIPTION field
        m = Pattern.compile("DISTRIB_DESCRIPTION=\"?([^\"]*)\"?$").matcher(result);
        if (m.find()) result = m.group(1);

        // If we have multiple lines, keep only the first line
        // (which we know is non-empty since result has been trimmed by readFirstFile())
        result = result.split("\n")[0];

        // Remove /etc/issue's format characters
        result = result.replaceAll("\\\\[a-zA-Z]", "");

        result = result.trim();

        _fullOSName = !result.isEmpty() ? result : "Linux";
        return _fullOSName;
    }

    /**
     * Helper method for getFullOSName()
     * Returns the content of the first file in 'dir' matching 'pattern', or an empty string if
     * no file is found.
     * Note: extra whitespaced is trimmed from the return value
     */
    private String readFirstFile(String dir, final Pattern pattern)
    {
        File[] files = new File(dir).listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File arg0, String name)
            {
                return pattern.matcher(name).matches();
            }
        });

        if (files == null || files.length == 0) return "";

        try {
            return Files.toString(files[0], Charsets.UTF_8).trim();
        } catch (IOException e) {
            l.warn("trying to read " + files[0] + " - " + Util.e(e));
            return "";
        }
    }

    @Override
    public void addToFavorite(String path) throws IOException
    {
        File f = new File(System.getenv("HOME") + File.separator + ".gtk-bookmarks");

        if (f.exists()) {
            FileWriter fw = new FileWriter(f,true);
            try {
                fw.write("file://" + path);
            } finally {
                fw.close();
            }
        }
    }

    @Override
    public void removeFromFavorite(String path) throws IOException
    {
        InjectableFile f = _factFile.create(Util.join(System.getenv("HOME"), ".gtk-bookmarks"));
        InjectableFile tmpFile = _factFile.createTempFile(".gtk-bookmarks","$$$");

        String currentLine;
        BufferedReader reader = new BufferedReader(new FileReader(f.getImplementation()));
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(tmpFile.getImplementation()));

            try {
                while ((currentLine = reader.readLine()) != null) {
                    if (currentLine.contains(path)) continue;
                    writer.write(currentLine);
                }
            } finally {
                if (writer != null) writer.close();
            }
            tmpFile.copy(f, false, false);
            tmpFile.deleteOrOnExit();
        } finally {
            if (reader != null) reader.close();
        }
    }

    @Override
    public String getFileSystemType(String path, OutArg<Boolean> remote) throws IOException
    {
        OutArg<String> output = new OutArg<String>();
        SystemUtil.execForeground(output, "stat", "-f", "--format=%T", path);
        String fstype = output.get().trim();
        remote.set(fstype.equals("nfs"));
        return fstype;
    }

    @Override
    public boolean isFileSystemTypeSupported(String type, Boolean remote)
    {
        String[] fss = new String[] { "EXT", "NFS", "BTRFS", "ECRYPTFS", "VZFS",
                "REISER", "XFS", "UFS", "CRYPT", "JFS", "SIMFS",
                "UNKNOWN (0X565A4653)", // VZFS when stat doesn't know about that magic number
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
    public String getShellExtensionChecksum()
    {
        return "";
    }

    @Override
    public void installShellExtension(boolean silently)
    {
        // Shell extensions not yet implemented on Linux
    }

    @Override
    public void startShellExtension(int port)
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
            l.warn("showInFolder failed: " + Util.e(e));
        }
    }
}

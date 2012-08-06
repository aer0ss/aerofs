
package com.aerofs.lib.os;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import com.aerofs.l.L;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.injectable.InjectableFile;

public class OSUtilLinux extends AbstractOSUtilLinuxOSX
{
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
    public void remFromFavorite(String path) throws IOException
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
    public boolean isFileSystemTypeSupported(String type, Boolean remote)
    {
        // we allow rootfs but it's risky to use it as it's based on RAM and
        // every time the computer reboots folders disappear. If the user
        // then creates an empty AeroFS folder all of his libraries will be
        // deleted.
        //
        // http://www.kernel.org/doc/Documentation/filesystems/ramfs-rootfs-initramfs.txt
        //
        // Now that we don't mistakenly mark folder on / as rootfs, we should be able to safely
        // drop rootfs from the list here.
        String[] fss = new String[] { "EXT", "NFS", "BTRFS", "ECRYPTFS",
                "REISERFS", "ROOTFS", "XFS", "UFS", "CRYPT", "JFS", "SIMFS" };
        for (String fs : fss) if (type.startsWith(fs)) return true;
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
    public String getIconFileExtension()
    {
        return "png";
    }

    @Override
    public void showInFolder(String path)
    {
        try {
            // nautilus is the default file manager for Gnome
            // TODO (GS): On KDE, should use dolphin
            // Or better yet: we should expose an option so that our linux friends can specify their
            // favorite file manager.
            Util.execBackground("nautilus", path);
        } catch (IOException e) {
            l.warn("showInFolder failed: " + Util.e(e));
        }
    }
}

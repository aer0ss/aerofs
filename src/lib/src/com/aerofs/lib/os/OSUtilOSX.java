package com.aerofs.lib.os;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import com.aerofs.lib.AppRoot;
import com.aerofs.lib.L;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.SecUtil;
import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.injectable.InjectableFile;
import com.aerofs.swig.driver.Driver;
import com.aerofs.swig.driver.DriverConstants;
import com.google.inject.Inject;

public class OSUtilOSX extends AbstractOSUtilLinuxOSX
{
    @Inject
    public OSUtilOSX(InjectableFile.Factory factFile)
    {
        super(factFile);
    }

    @Override
    public final String getDefaultRTRoot()
    {
        String path = System.getenv("HOME") + "/Library/Application Support/" + L.PRODUCT;
        if (Cfg.staging()) path += ".staging";
        return path;
    }

    /**
     * Runs one or more shell commands with admin privileges.
     * The user will be prompted to type his password to proceed.
     * @param prompt message that will be displayed in the dialog box
     * @param commands commands to run
     * @throws SecurityException if the user fails to authenticate with his password
     * @throws IOException if there's a problem while running the commands
     */
    private void runWithAdminPrivileges(String prompt, ArrayList<String> commands) throws IOException, SecurityException
    {
        // To run several commands at once, we concatenate them with ";" and run them through /bin/sh
        ArrayList<String> result = new ArrayList<String>();
        result.addAll(Arrays.asList(
                AppRoot.abs().concat("/AeroFSsetup"),
                "--icon=" + AppRoot.abs() + "/../AeroFS32.icns",
                "--prompt=" + prompt,
                "/bin/sh",
                "-c"
        ));

        StringBuilder cmd = new StringBuilder();
        for (String s : commands) {
            if (s != null) {
                cmd.append(s);
                cmd.append(" ; ");
            }
        }
        result.add(cmd.toString());

        String[] cmdsArray = result.toArray(new String[result.size()]);
        int r = SystemUtil.execForeground(cmdsArray);
        if (r != 0) {
            l.warn("User denied authorization to run privileged command (return code: " + r + ")");
            throw new SecurityException("Authorization denied");
        }
    }

    @Override
    public OSUtil.OSFamily getOSFamily()
    {
        return OSUtil.OSFamily.OSX;
    }

    @Override
    public String getFullOSName()
    {
        return System.getProperty("os.name") + " " + System.getProperty("os.version");
    }

    @Override
    public void addToFavorite(String path) throws IOException
    {
        remFromFavorite(path);
        SystemUtil.execBackground(AppRoot.abs().concat("/osxtools"), "shortcut", "add", path);
    }

    @Override
    public void remFromFavorite(String path) throws IOException
    {
        SystemUtil.execBackground(AppRoot.abs().concat("/osxtools"), "shortcut", "rem", path);
    }

    @Override
    public boolean isFileSystemTypeSupported(String type, Boolean remote)
    {
        String[] fss = new String[] { "HFS", "NFS", "AFPFS", "Z410_STORAGE" };
        for (String fs : fss) if (type.startsWith(fs)) return true;
        return false;
    }

    static final String FINDEREXT_BUNDLE = "AeroFSFinderExtension.osax";
    static final String FINDEREXT_DIR = "/Library/ScriptingAdditions/" + FINDEREXT_BUNDLE;

    @Override
    public String getShellExtensionName()
    {
        return "Finder Extension";
    }

    @Override
    public boolean isShellExtensionAvailable()
    {
        return true;
    }

    @Override
    public boolean isShellExtensionInstalled()
    {
        // Check if the /Contents directory is a symlink
        // TODO (GS): This is needed to transition from the previous state when we were copying
        // rather than symlinking. We can remove this check and only test for existance once
        // we believe everybody has made the transition.
        boolean isSymlink;
        try {
            isSymlink = isSymlink(FINDEREXT_DIR + "/Contents");
        } catch (IOException e) {
            l.warn("isSymlink failed: " + Util.e(e));
            isSymlink = true; // in case of failure, assume success since it's not a critical check
        }

        return isSymlink
                && (new File(FINDEREXT_DIR + "/Contents/Resources/finder_inject")).exists();
    }

    /**
     * Returns true if 'path' is a symbolic link
     * Returns false if path doesn't exist or isn't a symbolic link
     */
    private boolean isSymlink(String path) throws IOException
    {
        int ret = Driver.getFid(null, path, null);
        return (ret == DriverConstants.GETFID_SYMLINK);
    }

    /**
     * Returns the owner of a file or directory
     */
    private String getOwner(File file) throws IOException
    {
        OutArg<String> result = new OutArg<String>();
        SystemUtil.execForeground(result, "/bin/sh", "-c",
                "ls -l " + file.getParent() + " | grep " + file.getName() + " | awk '{print $3}'");
        return result.get().trim();
    }

    private int _shellextPort;

    @Override
    public String getShellExtensionChecksum()
    {
        File f = new File(AppRoot.abs() + "/" + FINDEREXT_BUNDLE +
                "/Contents/MacOS/AeroFSFinderExtension");
        try {
            return Util.hexEncode(SecUtil.hash(f));
        } catch (IOException e) {
            l.warn("Could not compute hash for " + f + ": " + Util.e(e));
            return "";
        }
    }

    @Override
    public void installShellExtension(boolean silently) throws IOException, SecurityException
    {
        l.debug("Installing the Finder extension");

        // Check that the we didn't forget to ship the Finder extension with AeroFS
        File source = new File(AppRoot.abs() + "/" + FINDEREXT_BUNDLE);
        if (!source.exists()) {
            l.warn("Could not install Finder extension because the installation package does not" +
                    " exist at " + source.getAbsolutePath());
            return;
        }

        File destination = new File(FINDEREXT_DIR);

        // Check that the destination folder exists and is not owned by root
        // It could be owned by root if we're updating from an older AeroFS client, because we used
        // to not change the owner to the user.
        // TODO (GS): Remove the ownership test once we believe no one is running an older client
        if (!destination.exists() || getOwner(destination).equals("root")) {
            if (!silently) {
                l.debug("Creating the directories for the Finder extension");
                ArrayList<String> commands = new ArrayList<String>();
                commands.add("mkdir -p " + destination.getPath());
                commands.add("chown $USER " + destination.getPath());
                runWithAdminPrivileges(L.PRODUCT + " needs your password to install the "
                        + getShellExtensionName() + ".", commands);
            } else {
                throw new SecurityException("Needs admin privileges");
            }
        }

        // At this point, we know that the destination folder exists and is owned by a user (but
        // not necessarily the current user).
        // Let's try to remove it and recreate it as a symlink

        OutArg<String> result = new OutArg<String>();
        int retVal = SystemUtil.execForeground(result, "rm", "-rf", FINDEREXT_DIR + "/Contents");
        if (retVal != 0) {
            throw new IOException("Failed to remove old Finder extension:\n" + result.get());
        }

        SystemUtil.execForeground("ln", "-s", source.getAbsolutePath() + "/Contents",
                FINDEREXT_DIR + "/Contents");

        l.debug("Restarting the Finder");
        SystemUtil.execForeground("killall", "Finder");

        if (_shellextPort > 0) {
            startShellExtension(_shellextPort);
        }
    }

    @Override
    public void startShellExtension(final int port)
    {
        // Save the port we used so that if this attempt to start the shell extension fails because
        // it's not installed, we can retry to start it after installShellExtension()
        _shellextPort = port;

        if (!isShellExtensionInstalled()) {
            l.warn("Finder Extension not found - not launching");
            return;
        }

        try {
            SystemUtil.execBackground(FINDEREXT_DIR + "/Contents/Resources/finder_inject",
                    Integer.toString(port));
        } catch (IOException e) {
            l.warn("Unable to launch Finder extension " + Util.e(e));
        }

        l.debug("Finder extension launched");
    }

    @Override
    public void showInFolder(String path)
    {
        try {
            SystemUtil.execBackground("open", "-R", path);
        } catch (IOException e) {
            l.warn("showInFolder failed: " + Util.e(e));
        }
    }

    @Override
    public String getFileSystemType(String path, OutArg<Boolean> remote)
            throws IOException
    {
        byte[] buffer = new byte[256]; // I doubt any filesystem has a name longer than 256 chars
        int rc = Driver.getFileSystemType(null, path, buffer, buffer.length);
        // not using switch/case because we don't want to statically import Driver's constants
        if (rc == Driver.FS_LOCAL) {
            remote.set(false);
        } else if (rc == Driver.FS_REMOTE) {
            remote.set(true);
        } else {
            throw new IOException("Couldn't get filesystem type: " + path + ". Error code: " + rc);
        }
        return Util.cstring2string(buffer, false);
    }

}

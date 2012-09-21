
package com.aerofs.lib.os;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import com.aerofs.swig.driver.Driver;
import org.apache.log4j.Logger;

import com.aerofs.lib.C;
import com.aerofs.lib.OutArg;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.injectable.InjectableFile;

abstract class AbstractOSUtilLinuxOSX implements IOSUtil
{
    protected static final Logger l = Util.l(AbstractOSUtilLinuxOSX.class);

    protected final InjectableFile.Factory _factFile;

    protected AbstractOSUtilLinuxOSX(InjectableFile.Factory factFile)
    {
        loadLibrary("aerofsd");
        _factFile = factFile;
    }

    @Override
    public final void loadLibrary(String library)
    {
        System.loadLibrary(library);
    }

    @Override
    public final String getDefaultRootAnchorParent()
    {
        return System.getProperty("user.home");
    }

    @Override
    public String getAuxRoot(String path) throws IOException
    {
        String def = Cfg.absDefaultAuxRoot();
        String mntDef = getMountPoint(def);
        String mnt = getMountPoint(path);
        if (mnt.equals(mntDef)) {
            return def;
        } else {
            return Util.join(mnt, C.AUXROOT_PARENT, Cfg.did().toStringFormal());
        }
    }

    /**
     * @param path can be relative, non-canonical path
     */
    protected static String getMountPoint(String path) throws IOException
    {
        File f = new File(path);
        int bufferLen = Driver.getMountIdLength();
        byte[] buffer1 = new byte[bufferLen];
        byte[] buffer2 = new byte[bufferLen];
        int rc;
        rc = Driver.getMountIdForPath(null, path, buffer1);
        if (rc != 0) throw new IOException("Failed to get mount point: " + path);
        // Walk up the filesystem tree until you hit a node with a new mount ID or the root
        while (true) {
            File parent = f.getParentFile();
            if (parent == null || parent.equals(f)) { // we've hit the root
                return f.toString();
            }
            rc = Driver.getMountIdForPath(null, parent.toString(), buffer2);
            if (rc != 0) throw new IOException("Failed to get mount point: " + parent.toString());
            if (!Arrays.equals(buffer1, buffer2)) { // we've crossed filesystem boundaries
                return f.toString();
            }
            f = parent;
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

    @Override
    public boolean isInSameFileSystem(String p1, String p2) throws IOException
    {
        int bufferlen = Driver.getMountIdLength();
        byte[] fsid1 = new byte[bufferlen];
        byte[] fsid2 = new byte[bufferlen];
        int rc;
        rc = Driver.getMountIdForPath(null, p1, fsid1);
        if (rc != 0) throw new IOException("Couldn't get mount id for " + p1);
        rc = Driver.getMountIdForPath(null, p2, fsid2);
        if (rc != 0) throw new IOException("Couldn't get mount id for " + p1);
        return Arrays.equals(fsid1, fsid2);
    }

    @Override
    public void markHiddenSystemFile(String absPath)
    {
        // hidden system files should start with dots on UNIX systems
        assert new File(absPath).getName().charAt(0) == '.';
    }

    /* cp is used instead of Java to preserve resource forks
     *
     * P: does not follow symbolic links
     * R: recursive: note that in MAC OS X only the contents are copied from a directory if
     * source path ends in / and the actual folder is copied if not
     * This does not seem to be a problem right now but AeroFS/AeroFS might happen
     *
     * n: do not overwrite existing file (exclusive)
     * f: force (not exclusive)
     * p: preserve attributes of file (keepMTime, note this will be more extensive than just mtime)
     */
    @Override
    public void copyRecursively(InjectableFile from, InjectableFile to, boolean exclusive,
            boolean keepMTime)
            throws IOException
    {
        String absFromPath = from.getAbsolutePath();
        String absToPath = to.getAbsolutePath();

        String arguments;
        if (exclusive) {
            arguments = keepMTime ? "-npPR" : "-nPR";
        } else {
            arguments = keepMTime ? "-fpPR" : "-fPR";
        }

        int exitCode = Util.execForeground("cp", arguments, absFromPath, absToPath);
        if (exitCode != 0) {
            throw new IOException("cp " + absFromPath + " to " + absToPath + "failed with exit " +
                    "code: " + exitCode);
        }
    }
}

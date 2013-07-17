
package com.aerofs.lib.os;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import com.aerofs.base.Loggers;
import com.aerofs.config.properties.DynamicOptionalStringProperty;
import com.aerofs.lib.ProgressIndicators;
import com.aerofs.lib.SystemUtil;
import com.aerofs.swig.driver.Driver;
import org.slf4j.Logger;

import com.aerofs.lib.injectable.InjectableFile;

abstract class AbstractOSUtilLinuxOSX implements IOSUtil
{
    protected static final Logger l = Loggers.getLogger(AbstractOSUtilLinuxOSX.class);
    protected final InjectableFile.Factory _factFile;
    protected final ProgressIndicators _pi;

    protected AbstractOSUtilLinuxOSX(InjectableFile.Factory factFile)
    {
        loadLibrary("aerofsd");
        _factFile = factFile;
        _pi = ProgressIndicators.get();  // sigh, this should be injected
    }

    @Override
    public final void loadLibrary(String library)
    {
        System.loadLibrary(library);
    }

    protected String getDefaultRootAnchorParentImpl(DynamicOptionalStringProperty property)
    {
        return property.get().isPresent()
                ? OSUtil.replaceEnvironmentVariables(property.get().get())
                : getUserHomeDir();
    }

    @Override
    public String getUserHomeDir()
    {
        return System.getProperty("user.home");
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
        if (rc != 0) throw new IOException("Couldn't get mount id for " + p2);
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

        try {
            _pi.startSyscall();
            int exitCode = SystemUtil.execForeground("cp", arguments, absFromPath, absToPath);
            if (exitCode != 0) {
                throw new IOException("cp " + absFromPath + " to " + absToPath +
                        "failed with exit code: " + exitCode);
            }
        } finally {
            _pi.endSyscall();
        }
    }
}

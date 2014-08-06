package com.aerofs.lib.injectable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import com.aerofs.lib.SystemUtil;
import com.aerofs.lib.ex.ExFileNoPerm;
import com.aerofs.lib.ex.ExFileNotFound;
import com.aerofs.lib.ex.ExFileIO;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.os.IOSUtil;
import com.aerofs.lib.os.OSUtil.OSFamily;
import com.aerofs.swig.driver.Driver;
import com.google.inject.Inject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.aerofs.swig.driver.DriverConstants.*;
import static com.google.common.base.Preconditions.checkState;

/**
 * The injectable wrapper for Driver
 */
public class InjectableDriver
{
    private final int _lenFID;
    private final IOSUtil _osutil;

    @Inject
    public InjectableDriver(IOSUtil osutil)
    {
        _osutil = osutil;
        _osutil.loadLibrary("aerofsd");

        // cache the result to avoid unecessary JNI calls
        _lenFID = Driver.getFidLength();
    }

    public int getFIDLength()
    {
        return _lenFID;
    }

    // Note: these error lists are not intended to be complete, but simply a listing of certain
    // error codes that are relevant for InjectableDriver that we can handle intelligently
    // From http://msdn.microsoft.com/en-us/library/windows/desktop/ms681382(v=vs.85).aspx
    private class WindowsErrnoList
    {
        public static final int FILE_NOT_FOUND = 2;     // Final path component didn't exist
        public static final int PATH_NOT_FOUND = 3;     // Non-final path component didn't exist
        public static final int ACCESS_DENIED = 5;      // User lacks permissions
        public static final int SHARING_VIOLATION = 32; // File already opened exclusively

        // specific to ReplaceFile
        private final static int UNABLE_TO_MOVE_REPLACED = 0x497;
        private final static int UNABLE_TO_MOVE_REPLACEMENT = 0x498;
        private final static int UNABLE_TO_MOVE_REPLACEMENT_2 = 0x499;

        private final static int CANT_ACCESS_FILE = 0x780;
    }

    // From /usr/include/asm-generic/errno-base.h from a Linux box.
    //   Base errno values are given by the UNIX specification.
    private class NixErrnoList
    {
        public static final int EPERM = 1;   // Operation not permitted
        public static final int ENOENT = 2;  // No such file or directory
        public static final int EIO = 5;     // I/O error
        public static final int EACCES = 13; // Permission denied
    }

    public static class FIDAndType {
        public final FID _fid;
        public final boolean _dir;

        public FIDAndType(FID fid, boolean dir)
        {
            _fid = fid;
            _dir = dir;
        }
    }

    public static class CpuUsage {
        public long kernel_time_nanos;
        public long user_time_nanos;
        public CpuUsage(long kernel_time, long user_time)
        {
            kernel_time_nanos = kernel_time;
            user_time_nanos = user_time;
        }
    }

    /**
     * @return null for OS-specific files
     */
    public @Nullable FIDAndType getFIDAndTypeNullable(String absPath)
            throws IOException
    {
        File f = new File(absPath);
        assert f.isAbsolute() : absPath;
        byte[] bs = new byte[getFIDLength()];
        int ret = Driver.getFid(null, absPath, bs);
        if (ret == DRIVER_FAILURE_WITH_ERRNO) throwExceptionByErrno(errnoPackedInFid(bs), f);
        if (ret == DRIVER_FAILURE) throwNotFoundOrIOException(f);
        if (ret != GETFID_FILE && ret != GETFID_DIR) return null;
        return new FIDAndType(new FID(bs), ret == GETFID_DIR);
    }

    private void throwExceptionByErrno(int errno, File f)
            throws IOException
    {
        if (_osutil.getOSFamily() == OSFamily.WINDOWS) {
            switch (errno) {
            case WindowsErrnoList.FILE_NOT_FOUND:
            case WindowsErrnoList.PATH_NOT_FOUND:
                throw new ExFileNotFound(f);
            case WindowsErrnoList.ACCESS_DENIED:
            case WindowsErrnoList.CANT_ACCESS_FILE:
                throw new ExFileNoPerm(f);
            case WindowsErrnoList.SHARING_VIOLATION:
                throw new ExFileIO("Another process has {} open", f);
            default:
                throw new ExFileIO("Unhandled error " + errno + " on {}", f);
            }
        } else {
            switch (errno) {
            case NixErrnoList.EACCES:
            case NixErrnoList.EPERM:
                throw new ExFileNoPerm(f);
            case NixErrnoList.EIO:
                throw new ExFileIO("IO failure on {}", f);
            case NixErrnoList.ENOENT:
                throw new ExFileNotFound(f);
            default:
                throw new ExFileIO("Unhandled error " + errno + " on {}", f);
            }
        }
    }

    /**
     * @return null on OS-specific files
     */
    final public @Nullable FID getFID(String absPath) throws IOException
    {
        FIDAndType fnt = getFIDAndTypeNullable(absPath);
        return fnt == null ? null : fnt._fid;
    }
    public static class ReplaceFileException extends IOException
    {
        private static final long serialVersionUID = 0L;

        public final boolean replacedMovedToBackup;

        private ReplaceFileException(String message, boolean moved)
        {
            super(message);
            replacedMovedToBackup = moved;
        }
    }

    public void replaceFile(@Nonnull String replaced, @Nonnull String replacement,
            @Nonnull String backup) throws IOException
    {
        // only supported on Widows for now
        checkState(_osutil.getOSFamily() == OSFamily.WINDOWS);

        int ret = Driver.replaceFile(null, replaced, replacement, backup);
        if (ret == DRIVER_SUCCESS) return;
        switch (ret) {
        case WindowsErrnoList.FILE_NOT_FOUND:
        case WindowsErrnoList.PATH_NOT_FOUND:
            throw new FileNotFoundException("replaced or replacement file missing");
        case WindowsErrnoList.ACCESS_DENIED:
            throw new ExFileIO("access to file(s) denied",
                    new File(replaced), new File(replacement));
        case WindowsErrnoList.SHARING_VIOLATION:
            throw new ExFileIO("file(s) open by another process",
                    new File(replaced), new File(replacement));
        case WindowsErrnoList.UNABLE_TO_MOVE_REPLACED:
            throw new ReplaceFileException("could not delete " + replaced, false);
        case WindowsErrnoList.UNABLE_TO_MOVE_REPLACEMENT:
            throw new ReplaceFileException("could not move " + replacement, false);
        case WindowsErrnoList.UNABLE_TO_MOVE_REPLACEMENT_2:
            throw new ReplaceFileException("could not move " + replacement, true);
        default:
            throw new IOException("ReplaceFile failed: " + ret);
        }
    }

    /**
     * If the file does not exist, then throw a detailed ExFileNotFound exception. Otherwise,
     * throws a generic ExFileIO exception.
     *
     * @param f The file to check for existence
     * @throws ExFileIO if the object is not present
     * @throws IOException if getFID failed for other reasons
     */
    private void throwNotFoundOrIOException(File f)
            throws IOException
    {
        if (f.exists()) throwIOException(f);
        else throw new ExFileIO("file {} didn't exist", f);
    }

    private void throwIOException(File f) throws IOException
    {
        if (f.exists()) {
            throw new ExFileIO("getFid: {}", f);
        } else {
            throw new ExFileNotFound(f);
        }
    }

    public void setFolderIcon(String folderPath, String iconName)
    {
        Driver.setFolderIcon(null, folderPath, iconName);
    }

    public int killDaemon()
    {
        return Driver.killDaemon();
    }
    private static int errnoPackedInFid(byte[] bs)
    {
        assert bs.length >= 4;
        // Java doesn't have unsigned types.  FML.
        // Driver guarantees that we pack the error code in as a big endian int in the first
        // 4 bytes
        int b1 = (0xff & (bs[0])) * 0x01000000;
        int b2 = (0xff & (bs[1])) * 0x00010000;
        int b3 = (0xff & (bs[2])) * 0x00000100;
        int b4 = (0xff & (bs[3]));
        return b1 + b2 + b3 + b4;
    }
    public CpuUsage getCpuUsage()
    {
        final com.aerofs.swig.driver.CpuUsage NativeCpuUsage = Driver.getCpuUsage();
        long ktime = NativeCpuUsage.getKernel_time();
        long utime = NativeCpuUsage.getUser_time();
        if (ktime < 0) {
            // Fatal system error.  Error code passed in utime.
            SystemUtil.fatal("Couldn't get CPU usage: " + utime);
        }
        return new CpuUsage(ktime, utime);
    }

}

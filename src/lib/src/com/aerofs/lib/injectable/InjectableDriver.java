package com.aerofs.lib.injectable;

import java.io.File;
import java.io.IOException;

import com.aerofs.lib.ex.ExFileNotFound;
import com.aerofs.lib.ex.ExFileIO;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.swig.driver.Driver;
import com.google.inject.Inject;

import javax.annotation.Nullable;

import static com.aerofs.swig.driver.DriverConstants.*;

/**
 * The injectable wrapper for Driver
 */
public class InjectableDriver
{
    private final int _lenFID;

    @Inject
    public InjectableDriver()
    {
        OSUtil.get().loadLibrary("aerofsd");

        // cache the result to avoid unecessary JNI calls
        _lenFID = Driver.getFidLength();
    }

    public int getFIDLength()
    {
        return _lenFID;
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

    /**
     * @return null on OS-specific files
     */
    public @Nullable FIDAndType getFIDAndType(String absPath)
            throws IOException
    {
        File f = new File(absPath);
        assert f.isAbsolute() : absPath;
        byte[] bs = new byte[getFIDLength()];
        int ret = Driver.getFid(null, absPath, bs);
        if (ret == DRIVER_FAILURE) throwNotFoundOrIOException(f);
        if (ret != GETFID_FILE && ret != GETFID_DIR) return null;
        return new FIDAndType(new FID(bs), ret == GETFID_DIR);
    }

    /**
     * @return null on OS-specific files
     */
    final public @Nullable FID getFID(String absPath) throws IOException
    {
        FIDAndType fnt = getFIDAndType(absPath);
        return fnt == null ? null : fnt._fid;
    }

    /**
     * If the file does not exist, then throw a detailed ExFileNotFound exception. Otherwise,
     * throws a generic ExFileIO exception.
     *
     * @param f The file to check for existence
     */
    private void throwNotFoundOrIOException(File f) throws IOException
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
}

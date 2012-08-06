package com.aerofs.lib.injectable;

import java.io.IOException;

import com.aerofs.lib.id.FID;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.swig.driver.Driver;
import com.aerofs.swig.driver.LogLevel;
import static com.aerofs.swig.driver.DriverConstants.*;

/**
 * The injectable wrapper for Driver
 */
public class InjectableDriver
{
    private final int _lenFID;

    public InjectableDriver()
    {
        OSUtil.get().loadLibrary("aerofsd");

        // cache the result to avoid unecessary JNI calls
        _lenFID = Driver.getFidLength();
    }

    public void initLogger_(String rtRoot, String name, LogLevel loglevel)
    {
        Driver.initLogger_(rtRoot, name, loglevel);
    }

    public int getPID()
    {
        return Driver.getPid();
    }

    public boolean killProcess(int pid)
    {
        return Driver.killProcess(pid);
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
     * @throws IOException if getting FID failed
     */
    public FIDAndType getFIDAndType(String path) throws IOException
    {
        byte[] bs = new byte[getFIDLength()];
        int ret = Driver.getFid(null, path, bs);
        if (ret == GETFID_ERROR) throwIOException(path);
        if ((ret & GETFID_FILE_OR_DIR) == 0) return null;
        return new FIDAndType(new FID(bs), ret == GETFID_DIR);
    }

    /**
     * @return null on OS-specific files
     * @throws IOException if getting FID failed
     */
    public FID getFID(String path) throws IOException
    {
        byte[] bs = new byte[getFIDLength()];
        int ret = Driver.getFid(null, path, bs);
        if (ret == GETFID_ERROR) throwIOException(path);
        if ((ret & GETFID_FILE_OR_DIR) == 0) return null;
        return new FID(bs);
    }

    private static void throwIOException(String path) throws IOException
    {
        throw new IOException("getFid: " + path);
    }

    public void setFolderIcon(String folderPath, String iconName)
    {
        Driver.setFolderIcon(null, folderPath, iconName);
    }
}

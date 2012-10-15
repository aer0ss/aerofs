package com.aerofs.lib.injectable;

import java.io.File;
import java.io.IOException;

import com.aerofs.lib.FileUtil;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.swig.driver.Driver;

import javax.annotation.Nullable;

import static com.aerofs.lib.PathObfuscator.obfuscate;
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
    public @Nullable FIDAndType getFIDAndType(String path)
            throws IOException, ExNotFound
    {
        byte[] bs = new byte[getFIDLength()];
        int ret = Driver.getFid(null, path, bs);
        if (ret == DRIVER_FAILURE) throwNotFoundOrIOException(path);
        if (ret != GETFID_FILE && ret != GETFID_DIR) return null;
        return new FIDAndType(new FID(bs), ret == GETFID_DIR);
    }

    /**
     * @return null on OS-specific files
     */
    public @Nullable FID getFID(String path) throws IOException
    {
        byte[] bs = new byte[getFIDLength()];
        int ret = Driver.getFid(null, path, bs);
        if (ret == DRIVER_FAILURE) throwIOException(path);
        if (ret != GETFID_FILE && ret != GETFID_DIR) return null;
        return new FID(bs);
    }

    /**
     * @throws ExNotFound if the object is not present
     * @throws IOException if getFID failed for other reasons
     */
    private static void throwNotFoundOrIOException(String path)
            throws IOException, ExNotFound
    {
        if (new File(path).exists()) throwIOException(path);
        else throw new ExNotFound(obfuscate(path));
    }

    private static void throwIOException(String path) throws IOException
    {
        File f = new File(path);
        // TODO (MJ) this seems like a gross dependency from InjectableDriver->FileUtil
        // this should be refactored and perhaps even made non-static somehow?
        throw new IOException("getFid: " + FileUtil.debugString(f));
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

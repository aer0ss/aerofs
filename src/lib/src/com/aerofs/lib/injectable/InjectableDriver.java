package com.aerofs.lib.injectable;

import java.io.File;
import java.io.IOException;

import com.aerofs.lib.FileUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.swig.driver.Driver;
import com.google.common.base.Joiner;
import com.google.inject.Inject;

import javax.annotation.Nullable;

import static com.aerofs.swig.driver.DriverConstants.*;

/**
 * The injectable wrapper for Driver
 */
public class InjectableDriver
{
    private final int _lenFID;
    private final CfgLocalUser _cfgLocalUser;

    @Inject
    public InjectableDriver(CfgLocalUser cfgLocalUser)
    {
        OSUtil.get().loadLibrary("aerofsd");

        // cache the result to avoid unecessary JNI calls
        _lenFID = Driver.getFidLength();
        _cfgLocalUser = cfgLocalUser;
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
     * N.B. Windows requires path to be an absolute path
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
     * N.B. Windows requires path to be an absolute path
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
    private void throwNotFoundOrIOException(String path)
            throws IOException, ExNotFound
    {
        File f = new File(path);
        if (f.exists()) throwIOException(path);
        else throw new ExNotFound(FileUtil.debugString(f));
    }

    private void throwIOException(String path) throws IOException
    {
        File f = new File(path);
        // TODO (DF): remove after you're done debugging daniel@iswech.de
        String user = _cfgLocalUser.get();
        if (user != null && Util.crc32(user).equals("cdd85529")) { // daniel@iswech.de
            String[] children = f.getParentFile().list();
            String[] encodedChildren = new String[children.length + 1];
            // Log the parent folder bytes
            encodedChildren[0] = Util.hexEncode(Util.string2utf(f.getParentFile().getPath()));
            // And the
            for (int i = 0; i < children.length ; i++) {
                encodedChildren[i+1] = Util.hexEncode(Util.string2utf(children[i]));
            }
            throw new IOException("getFid: " + FileUtil.debugString(f) + " " +
                    Joiner.on("\n").join(encodedChildren));
        }
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

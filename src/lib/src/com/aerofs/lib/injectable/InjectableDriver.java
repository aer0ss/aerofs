package com.aerofs.lib.injectable;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import com.aerofs.lib.FileUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.ex.ExFileIO;
import com.aerofs.lib.ex.ExNotFound;
import com.aerofs.lib.id.FID;
import com.aerofs.lib.id.UserID;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.swig.driver.Driver;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
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
     */
    public @Nullable FIDAndType getFIDAndType(String absPath)
            throws IOException, ExNotFound
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
        try {
            FIDAndType fnt = getFIDAndType(absPath);
            return fnt == null ? null : fnt._fid;
        } catch (ExNotFound e) {
            // TODO (MJ) this is a hack: for some reason, getFID and getFIDAndType diverged in the
            // exception types thrown. Previously they were copy-pasted differing mainly in
            // Driver-failure handling. Perhaps callers of getFID can handle ExNotFound being
            // thrown, but I don't want to find out now.
            throw new IOException(e);
        }
    }

    /**
     * @throws ExNotFound if the object is not present
     * @throws IOException if getFID failed for other reasons
     */
    private void throwNotFoundOrIOException(File f)
            throws IOException, ExNotFound
    {
        if (f.exists()) throwIOException(f);
        else throw new ExNotFound(FileUtil.debugString(f));
    }

    private static final Set<String> verboseLoggingUserCRCs = ImmutableSet.of(
            "cdd85529", // daniel@iswech.de
            "2a1a2221"  // hola@hoyesmiercoles.com
            );

    private void throwIOException(File f) throws IOException
    {
        // TODO (DF/MJ): remove after you're done debugging daniel@iswech.de and
        // hola@hoyesmiercoles.com
        UserID user = _cfgLocalUser.get();
        if (user != null && verboseLoggingUserCRCs.contains(Util.crc32(user.toString()))) {
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
        throw new ExFileIO("getFid: {}", f);
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

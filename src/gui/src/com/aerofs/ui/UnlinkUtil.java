package com.aerofs.ui;

import com.aerofs.ids.SID;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.RootAnchorUtil;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;
import com.aerofs.lib.injectable.InjectableFile.Factory;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map.Entry;

public class UnlinkUtil
{
    /**
     * Helper function to share code between the unlink and unlink & wipe commands.
     */
    public static void unlink() throws SQLException, IOException
    {
        // Stop the daemon and other GUI services before deleting any files.
        UIGlobals.rap().stop();
        UIGlobals.rnc().stop();
        UIGlobals.dm().stopIgnoreException();

        cleanRtRootAndAuxRoots();
    }

    public static void cleanRtRootAndAuxRoots() throws SQLException, IOException
    {
        // Delete aux roots (partial downloads, conflicts and revision history)
        if (Cfg.storageType() == StorageType.LINKED) {
            for (Entry<SID, String> e : Cfg.getRoots().entrySet()) {
                RootAnchorUtil.cleanAuxRootForPath(e.getValue(), e.getKey());
            }
        } else {
            RootAnchorUtil.cleanAuxRootForPath(Cfg.absDefaultRootAnchor(), Cfg.rootSID());
        }

        // Delete device key and certificate.
        FileUtil.deleteIgnoreErrorRecursively(new File(Cfg.absRTRoot(), LibParam.DEVICE_KEY));
        FileUtil.deleteIgnoreErrorRecursively(new File(Cfg.absRTRoot(), LibParam.DEVICE_CERT));

        // Delete the device id
        Cfg.db().set(Key.DEVICE_ID, Key.DEVICE_ID.defaultValue());
        // Create the setup file.
        new Factory().create(Util.join(Cfg.absRTRoot(), LibParam.SETTING_UP)).createNewFile();
    }
}

package com.aerofs.ui;

import com.aerofs.ids.SID;
import com.aerofs.lib.*;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.injectable.InjectableFile.Factory;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map.Entry;

import static com.aerofs.lib.cfg.ICfgStore.DEVICE_ID;

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
        UIGlobals.chat().stop();

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
        FileUtil.deleteIgnoreErrorRecursively(new File(Cfg.absRTRoot(), ClientParam.DEVICE_KEY));
        FileUtil.deleteIgnoreErrorRecursively(new File(Cfg.absRTRoot(), ClientParam.DEVICE_CERT));

        // Delete the device id
        Cfg.db().set(DEVICE_ID, DEVICE_ID.defaultValue());
        // Create the setup file.
        new Factory().create(Util.join(Cfg.absRTRoot(), ClientParam.SETTING_UP)).createNewFile();
    }
}

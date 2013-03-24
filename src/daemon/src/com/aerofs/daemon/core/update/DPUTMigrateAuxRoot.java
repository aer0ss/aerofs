/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.base.Loggers;
import com.aerofs.lib.Param.AuxFolder;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.Param;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgAbsAuxRoot;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.sv.client.SVClient;
import com.aerofs.swig.driver.Driver;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class DPUTMigrateAuxRoot implements IDaemonPostUpdateTask
{
    private final static Logger l = Loggers.getLogger(DPUTMigrateAuxRoot.class);
    private final CfgAbsAuxRoot _cfgAbsAuxRoot;

    DPUTMigrateAuxRoot(CfgAbsAuxRoot cfgAbsAuxRoot)
    {
        _cfgAbsAuxRoot = cfgAbsAuxRoot;
    }

    @Override
    public void run() throws Exception
    {
        try {
            final String oldAuxRoot = getOldAuxRoot();
            final String newAuxRoot = _cfgAbsAuxRoot.get();
            FileUtil.mkdirs(new File(newAuxRoot));
            OSUtil.get().markHiddenSystemFile(newAuxRoot);

            for (AuxFolder auxFolder : Param.AuxFolder.values()) {
                File src = new File(oldAuxRoot, auxFolder._name);
                File dst = new File(newAuxRoot, auxFolder._name);
                l.info("Migrating aux folder from " + src + " to " + dst);

                if (!src.exists()) {
                    l.warn("file not found: " + src.getAbsolutePath());
                    continue;
                }

                FileUtil.moveInSameFileSystem(src, dst);
            }
        } catch (Throwable e) {
            l.error("Could not migrate aux root " + Util.e(e));
            SVClient.logSendDefectSyncIgnoreErrors(true, "migrating aux root failed", e);
            ExitCode.DPUT_MIGRATE_AUX_ROOT_FAILED.exit();
        }
    }

    private String getOldAuxRoot() throws IOException
    {
        if (OSUtil.isWindows()) {
            return getOldAuxRootWin(Cfg.absDefaultRootAnchor());
        } else {
            return getOldAuxRootOSXLinux(Cfg.absDefaultRootAnchor());
        }
    }

    /////////////////////////////////////////////////////////
    //
    // Old code from OSUtil to get the path to the aux root
    //
    ////////////////////////////////////////////////////////

    private String OLD_AUXROOT_PARENT = ".aerofs.aux";

    private String getOldAuxRootWin(String path) throws IOException
    {
        String def = new File(Cfg.absRTRoot()).getCanonicalPath();
        char driveDef = Character.toUpperCase(def.charAt(0));
        path = new File(path).getCanonicalPath();
        char drive = Character.toUpperCase(path.charAt(0));

        if (drive == driveDef || drive == '\\') {
            return def;
        } else {
            File auxRootParent = new File(Util.join(drive + ":", OLD_AUXROOT_PARENT));
            return Util.join(auxRootParent.getPath(), Cfg.did().toStringFormal());
        }
    }

    private String getOldAuxRootOSXLinux(String path) throws IOException
    {
        String def = Cfg.absRTRoot();
        String mntDef = getMountPoint(def);
        String mnt = getMountPoint(path);
        if (mnt.equals(mntDef)) {
            return def;
        } else {
            return Util.join(mnt, OLD_AUXROOT_PARENT, Cfg.did().toStringFormal());
        }
    }

    private String getMountPoint(String path) throws IOException
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
}

/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.base.Loggers;
import com.aerofs.ids.DID;
import com.aerofs.lib.LibParam.AuxFolder;
import com.aerofs.lib.FileUtil;
import com.aerofs.lib.LibParam;
import com.aerofs.lib.SystemUtil.ExitCode;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.os.OSUtil;
import com.aerofs.swig.driver.Driver;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static com.aerofs.defects.Defects.newDefectWithLogs;

public class DPUTMigrateAuxRoot implements IDaemonPostUpdateTask
{
    private final static Logger l = Loggers.getLogger(DPUTMigrateAuxRoot.class);

    @Override
    public void run() throws Exception
    {
        try {
            final String oldAuxRoot = getOldAuxRoot();
            final String newAuxRoot = deprecatedAbsAuxRoot();
            FileUtil.mkdirs(new File(newAuxRoot));
            OSUtil.get().markHiddenSystemFile(newAuxRoot);

            for (AuxFolder auxFolder : LibParam.AuxFolder.values()) {
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
            newDefectWithLogs("dput.migrate_aux_root")
                    .setMessage("migrating aux root failed")
                    .setException(e)
                    .sendSyncIgnoreErrors();
            ExitCode.DPUT_MIGRATE_AUX_ROOT_FAILED.exit();
        }
    }

    static String getOldAuxRoot() throws IOException
    {
        if (OSUtil.isWindows()) {
            return getOldAuxRootWin(Cfg.absDefaultRootAnchor());
        } else {
            return getOldAuxRootOSXLinux(Cfg.absDefaultRootAnchor());
        }
    }


    static String deprecatedAbsAuxRoot()
    {
        return deprecatedAbsAuxRootForPath(Cfg.absDefaultRootAnchor(), Cfg.did());
    }

    public static final String DEPRECATED_AUXROOT_PREFIX = ".aerofs.";

    /**
     * @return the location of the aux root for a given path
     * @param did to use to generate the path
     * This is needed because during setup we want to use this method to check if we have the
     * permission to create the aux root folder, but don't have the real did yet.
     */
    // new aux root location at the time this DPUT was created
    // later deprecated when aux root was moved under root anchor
    private static String deprecatedAbsAuxRootForPath(String path, DID did)
    {
        String shortDid = did.toStringFormal().substring(0, 6);
        File parent = new File(path).getParentFile();
        File auxRoot = new File(parent, DEPRECATED_AUXROOT_PREFIX + shortDid);
        return auxRoot.getAbsolutePath();
    }


    /////////////////////////////////////////////////////////
    //
    // Old code from OSUtil to get the path to the aux root
    //
    ////////////////////////////////////////////////////////

    private static String OLD_AUXROOT_PARENT = ".aerofs.aux";

    private static String getOldAuxRootWin(String path) throws IOException
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

    private static String getOldAuxRootOSXLinux(String path) throws IOException
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

    private static String getMountPoint(String path) throws IOException
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

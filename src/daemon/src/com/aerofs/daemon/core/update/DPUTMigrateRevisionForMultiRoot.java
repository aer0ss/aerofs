/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.lib.FileUtil;
import com.aerofs.lib.Param;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgAbsAuxRoot;

import java.io.File;

/**
 * Linked storage revisions are stored using path. For that to work in a multiroot configuration,
 * the SID needs to be added to the path:
 *
 * /foo/bar/.aerofs.${DID:0:6}/r
 *
 * becomes
 *
 * /foo/bar/.aerofs.${DID:0:6}/r/${rootSID}
 */
public class DPUTMigrateRevisionForMultiRoot implements IDaemonPostUpdateTask
{
    private final CfgAbsAuxRoot _absAuxRoot;

    DPUTMigrateRevisionForMultiRoot(CfgAbsAuxRoot absAuxRoot)
    {
        _absAuxRoot = absAuxRoot;
    }

    @Override
    public void run() throws Exception
    {
        if (Cfg.storageType() != StorageType.LINKED) return;

        String revFolder = Util.join(_absAuxRoot.get(), Param.AuxFolder.REVISION._name);
        File oldRevFolder = new File(revFolder);
        File tmpRevFolder = new File(revFolder + "-tmp");
        File newRevFolder = new File(oldRevFolder, Cfg.rootSID().toStringFormal());

        if (oldRevFolder.exists()) {
            FileUtil.rename(oldRevFolder, tmpRevFolder);
            FileUtil.mkdir(oldRevFolder);
            FileUtil.rename(tmpRevFolder, newRevFolder);
        }
    }
}

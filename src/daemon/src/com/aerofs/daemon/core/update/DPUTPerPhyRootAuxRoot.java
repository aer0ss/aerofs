/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.lib.FileUtil;
import com.aerofs.lib.cfg.Cfg;

import java.io.File;

/**
 * The aux root used to be a folder named .aerofs.${first 6 hex digits of DID} at the same level as
 * the root anchor
 *
 * It is now .aerofs.aux.${first 6 hex digits of SID} at the same level of each store root.
 *
 * In the future we would ideally store it under the store root but that requires more work to
 * correctly filter out notifications.
 */
public class DPUTPerPhyRootAuxRoot implements IDaemonPostUpdateTask
{
    @Override
    public void run() throws Exception
    {
        String oldAuxRoot = DPUTMigrateAuxRoot.deprecatedAbsAuxRoot();
        String newAuxRoot = Cfg.absDefaultAuxRoot();

        // don't try moving if the source does not exist: the physical storage will ensure that the
        // new aux root exists
        if (new File(oldAuxRoot).exists()) {
            FileUtil.rename(new File(oldAuxRoot), new File(newAuxRoot));
        }
    }
}

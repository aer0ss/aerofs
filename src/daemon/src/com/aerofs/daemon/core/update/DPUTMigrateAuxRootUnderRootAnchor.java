/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.lib.FileUtil;
import com.aerofs.lib.StorageType;
import com.aerofs.lib.cfg.Cfg;

import java.io.File;

/**
 * The aux root used to be a folder named .aerofs.${first 6 hex digits of DID} at the same level as
 * the root anchor
 *
 * It is now .aerofs.aux under the root anchor
 *
 * The is change has a number of benefits:
 *   - much reduced likelihood of permission issues on aux root
 *   - much reduced likelihood of aux root and root anchor being on different partitions
 *   - no disconnect between size of root anchor and size of revision folder
 *   - much simpler code for root relocation
 *   - each external root implicitly gets its own aux root without extra work
 *
 * The end result is that we'll be able to support external roots on arbitrary partitions (as long
 * as the underlying FS is supported), even at the root of a partition
 */
public class DPUTMigrateAuxRootUnderRootAnchor implements IDaemonPostUpdateTask
{
    @Override
    public void run() throws Exception
    {
        String oldAuxRoot = DPUTMigrateAuxRoot.deprecatedAbsAuxRoot();
        String newAuxRoot = Cfg.absDefaultAuxRoot();

        FileUtil.rename(new File(oldAuxRoot), new File(newAuxRoot));
    }
}

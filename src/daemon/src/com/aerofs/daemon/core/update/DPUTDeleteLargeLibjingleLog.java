/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.lib.FileUtil;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;

import java.io.File;

public class DPUTDeleteLargeLibjingleLog implements IDaemonPostUpdateTask
{
    @Override
    public void run()
            throws Exception
    {
        File ljlog = new File(Util.join(Cfg.absRTRoot(), "lj.log"));
        FileUtil.deleteOrOnExit(ljlog);
    }
}

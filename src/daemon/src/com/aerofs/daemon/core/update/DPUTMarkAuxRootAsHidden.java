/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.CfgAbsAuxRoot;
import com.aerofs.lib.os.OSUtil;
import org.apache.log4j.Logger;

import java.io.IOException;

/**
 * Fixes a bug in DPUTMigrateAuxRoot where the new aux root wasn't set as a hidden folder on Windows
 */
public class DPUTMarkAuxRootAsHidden implements IDaemonPostUpdateTask
{
    private final static Logger l = Util.l(DPUTMigrateAuxRoot.class);
    private final CfgAbsAuxRoot _cfgAbsAuxRoot;

    DPUTMarkAuxRootAsHidden(CfgAbsAuxRoot cfgAbsAuxRoot)
    {
        _cfgAbsAuxRoot = cfgAbsAuxRoot;
    }

    @Override
    public void run() throws Exception
    {
        try {
            if (OSUtil.isWindows()) {
                OSUtil.get().markHiddenSystemFile(_cfgAbsAuxRoot.get());
            }
        } catch (IOException e) {
            l.warn("Ignoring: " + Util.e(e));
        }
    }
}

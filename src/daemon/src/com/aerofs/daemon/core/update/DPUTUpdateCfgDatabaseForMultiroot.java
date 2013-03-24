/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.base.Loggers;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase;
import org.slf4j.Logger;

/**
 *
 */
public class DPUTUpdateCfgDatabaseForMultiroot implements IDaemonPostUpdateTask
{
    private static final Logger l = Loggers.getLogger(DPUTUpdateCfgDatabaseForMultiroot.class);

    private final CfgDatabase _cfgDB;

    public DPUTUpdateCfgDatabaseForMultiroot(CfgDatabase cfgDB)
    {
        _cfgDB = cfgDB;
    }

    @Override
    public void run() throws Exception
    {
        l.info("update conf for multiroot");
        _cfgDB.createRootTable_(null);
        if (_cfgDB.getRoots_().isEmpty()) {
            l.info("add default root {} {}", Cfg.rootSID(), Cfg.absDefaultRootAnchor());
            _cfgDB.addRoot_(Cfg.rootSID(), Cfg.absDefaultRootAnchor());
        }
    }
}

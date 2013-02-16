/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.update;

import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;

/**
 * This DPUT is only run by users updating from an old version of AeroFS, which therefore do not
 * need to run FirstLaunch if they installed before it was implemented
 */
public class DPUTSkipFirstLaunch implements IDaemonPostUpdateTask
{
    @Override
    public void run() throws Exception
    {
        Cfg.db().set(Key.FIRST_START, false);
    }
}

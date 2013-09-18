/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.cfg;

import com.aerofs.lib.LibParam.EnterpriseConfig;

public class CfgEnabledTransports
{
    public boolean isTcpEnabled()
    {
        return Cfg.useTCP();
    }

    public boolean isJingleEnabled()
    {
        return Cfg.useJingle() && !EnterpriseConfig.IS_ENTERPRISE_DEPLOYMENT;
    }

    public boolean isZephyrEnabled()
    {
        return Cfg.useZephyr();
    }
}

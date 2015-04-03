/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.cfg;

public class CfgEnabledTransports
{
    public boolean isTcpEnabled()
    {
        return Cfg.useTCP();
    }

    public boolean isZephyrEnabled()
    {
        return Cfg.useZephyr();
    }
}

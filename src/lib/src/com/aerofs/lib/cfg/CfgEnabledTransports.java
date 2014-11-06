/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.cfg;

import com.aerofs.lib.LibParam.PrivateDeploymentConfig;

public class CfgEnabledTransports
{
    public boolean isTcpEnabled()
    {
        return Cfg.useTCP();
    }

    public boolean isJingleEnabled()
    {
        // Conditions for jingle to be enabled:
        //  1. No "nostun" flag.
        //  2. Must be HC deployment.
        //  3. Must not be a kiwi.ki user (see https://aerofs.atlassian.net/browse/ENG-2190).
        return Cfg.useJingle() &&
                PrivateDeploymentConfig.isHybridDeployment() &&
                !Cfg.user().getString().endsWith("@kiwi.ki");
    }

    public boolean isZephyrEnabled()
    {
        return Cfg.useZephyr();
    }
}

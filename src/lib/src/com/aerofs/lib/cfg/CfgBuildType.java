package com.aerofs.lib.cfg;

/**
 * The build type, either staging or production
 */
public class CfgBuildType
{
    public boolean isStaging()
    {
        return Cfg.staging();
    }
}
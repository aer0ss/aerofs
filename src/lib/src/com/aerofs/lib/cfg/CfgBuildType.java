package com.aerofs.lib.cfg;

import com.aerofs.labeling.L;

/**
 * The build type, either staging or production
 */
public class CfgBuildType
{
    public boolean isStaging()
    {
        return L.get().isStaging();
    }
}

/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.cfg;

/**
 * The absolute path to the aux root
 */
public class CfgAbsAuxRoot
{
    public String get()
    {
        return Cfg.absAuxRoot();
    }

    public String forPath(String path)
    {
        return Cfg.absAuxRootForPath(path, Cfg.did());
    }
}

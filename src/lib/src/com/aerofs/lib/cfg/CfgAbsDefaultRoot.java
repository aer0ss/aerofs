/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.cfg;

/**
 * The absolute path to the root anchor
 */
public class CfgAbsDefaultRoot {

    public String get()
    {
        return BaseCfg.getInstance().absDefaultRootAnchor();
    }
}

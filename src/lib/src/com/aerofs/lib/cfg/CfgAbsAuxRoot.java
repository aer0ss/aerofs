/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.lib.cfg;

import com.aerofs.lib.os.OSUtil;

import java.io.IOException;

/**
 * The absolute path to the aux root
 */
public class CfgAbsAuxRoot
{
    public String get() throws IOException
    {
        return OSUtil.get().getAuxRoot(Cfg.absRootAnchor());
    }
}

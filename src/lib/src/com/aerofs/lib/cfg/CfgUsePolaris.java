/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.lib.cfg;

import com.aerofs.base.Lazy;

public class CfgUsePolaris
{
    private Lazy<Boolean> _polaris =
            new Lazy<>(() -> CfgUtils.enabledByFile(BaseCfg.getInstance().absRTRoot(), "polaris"));
    public boolean get()
    {
        return _polaris.get();
    }

}

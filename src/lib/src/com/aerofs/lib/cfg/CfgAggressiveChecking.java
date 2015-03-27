/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.cfg;

import com.aerofs.base.Lazy;
import com.aerofs.lib.LibParam;

public class CfgAggressiveChecking
{
    private Lazy<Boolean> _aggressiveChecks =
            new Lazy<>(() -> CfgUtils.enabledByFile(BaseCfg.getInstance().absRTRoot(), LibParam.AGGRESSIVE_CHECKS));
    public boolean get()
    {
        return _aggressiveChecks.get();
    }
}

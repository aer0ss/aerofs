/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.cfg;

import com.aerofs.base.Lazy;
import com.aerofs.lib.LibParam;

public class CfgEnabledTransports
{
    private Lazy<Boolean> _tcpEnabled =
            new Lazy<>(() -> CfgUtils.disabledByFile(BaseCfg.getInstance().absRTRoot(), LibParam.NOTCP));
    private Lazy<Boolean> _zephrEnabled =
            new Lazy<>(() -> CfgUtils.disabledByFile(BaseCfg.getInstance().absRTRoot(), LibParam.NOZEPHYR));

    public boolean isTcpEnabled()
    {
        return _tcpEnabled.get();
    }

    public boolean isZephyrEnabled()
    {
        return _zephrEnabled.get();
    }
}

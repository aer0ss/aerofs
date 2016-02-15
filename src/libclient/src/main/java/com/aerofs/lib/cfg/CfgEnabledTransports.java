/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.lib.cfg;

import static com.aerofs.base.config.ConfigurationProperties.*;

import com.aerofs.base.Lazy;
import com.aerofs.lib.ClientParam;

public class CfgEnabledTransports
{
    private Lazy<Boolean> _tcpEnabled =
            new Lazy<>(() -> CfgUtils.disabledByFile(BaseCfg.getInstance().absRTRoot(), ClientParam.NOTCP)
                    && getBooleanProperty( "base.lansync.enabled", true));
    private Lazy<Boolean> _zephrEnabled =
            new Lazy<>(() -> CfgUtils.disabledByFile(BaseCfg.getInstance().absRTRoot(), ClientParam.NOZEPHYR));

    public boolean isTcpEnabled()
    {
        return _tcpEnabled.get();
    }

    public boolean isZephyrEnabled()
    {
        return _zephrEnabled.get();
    }
}

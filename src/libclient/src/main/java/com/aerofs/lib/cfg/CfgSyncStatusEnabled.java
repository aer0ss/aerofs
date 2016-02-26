package com.aerofs.lib.cfg;

import com.aerofs.base.Lazy;
import com.aerofs.base.config.ConfigurationProperties;

/**
 * whether or not sync status is enabled
 *
 * TODO remove when sync status is no longer behind a flag
 */
public class CfgSyncStatusEnabled
{
    private Lazy<Boolean> _syncstatus = new Lazy<>(
            () -> CfgUtils.enabledByFile(BaseCfg.getInstance().absRTRoot(), "syncstatus"));

    public boolean get() {
        return ConfigurationProperties.getBooleanProperty("base.syncstatus.enabled", false)
                || _syncstatus.get();
    }
}

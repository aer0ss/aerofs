package com.aerofs.daemon.core.net;

import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.cfg.CfgDatabase.Key;

// FIXME (AG): this class is deprecated and should be combined into DeviceLRU to create a single DeviceCache
/**
 * See CoreQueue for design rationale of this class
 */
public class CoreDeviceLRU extends DeviceLRU
{
    // TODO use a unified session management system where a session is consistently deleted from
    // caches at all the layers at once
    private static int getSize()
    {
        assert Cfg.inited();

        return Math.max((Cfg.db().getInt(Key.MAX_SERVER_STACKS) * 20 +
                                 Cfg.db().getInt(Key.MAX_CLIENT_STACKS) * 2) * 20, 64);
    }

    public CoreDeviceLRU()
    {
        super(getSize());
    }
}

package com.aerofs.daemon.core;

import com.aerofs.daemon.core.net.device.DeviceLRU;
import com.aerofs.daemon.lib.DaemonParam;

/**
 * See CoreQueue for design rationale of this class
 */
public class CoreDeviceLRU extends DeviceLRU
{
    public CoreDeviceLRU()
    {
        super(DaemonParam.deviceLRUSize());
    }
}

package com.aerofs.daemon.core;

import com.aerofs.lib.id.DID;

public interface IDeviceEvictionListener
{
    void evicted_(DID did);
}

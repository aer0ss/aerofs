package com.aerofs.daemon.core;

import com.aerofs.base.id.DID;

public interface IDeviceEvictionListener
{
    void evicted_(DID did);
}

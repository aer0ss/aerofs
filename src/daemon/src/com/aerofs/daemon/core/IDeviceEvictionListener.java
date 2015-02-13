package com.aerofs.daemon.core;

import com.aerofs.ids.DID;

public interface IDeviceEvictionListener
{
    void evicted_(DID did);
}

package com.aerofs.daemon.core;

import com.aerofs.ids.DID;

public interface IDeviceEvictionListener
{
    // NB: MUST be thread-safe
    void evicted(DID did);
}

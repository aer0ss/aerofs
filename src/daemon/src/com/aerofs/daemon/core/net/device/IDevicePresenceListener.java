/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.net.device;

import com.aerofs.base.id.DID;

public interface IDevicePresenceListener
{
    void deviceOnline_(DID did);
    void deviceOffline_(DID did);
}

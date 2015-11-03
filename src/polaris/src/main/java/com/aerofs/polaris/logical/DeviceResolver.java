package com.aerofs.polaris.logical;

import com.aerofs.ids.DID;
import com.aerofs.ids.UserID;

public interface DeviceResolver {
    public UserID getDeviceOwner(DID device) throws NotFoundException;
}

/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.ExDeviceUnavailable;

public final class DevicePresenceListener implements IDevicePresenceListener
{
    private final IUnicastInternal unicast;

    public DevicePresenceListener(IUnicastInternal unicast)
    {
        this.unicast = unicast;
    }

    // FIXME: when can it arise that unicast has not disconnected a channel, but the
    // device in question is being marked offline? This may be fully vestigial.
    @Override
    public void onDevicePresenceChanged(DID did, boolean isPotentiallyAvailable)
    {
        if (!isPotentiallyAvailable) {
            unicast.disconnect(did, new ExDeviceUnavailable("remote offline"));
        }
    }
}

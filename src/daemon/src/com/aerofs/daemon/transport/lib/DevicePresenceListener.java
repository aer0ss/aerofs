/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.transport.lib;

import com.aerofs.base.id.DID;
import com.aerofs.daemon.transport.ExDeviceUnavailable;
import com.aerofs.rocklog.RockLog;

import static com.aerofs.daemon.transport.lib.TransportDefects.DEFECT_NAME_PULSE_SURVIVED_PRESENCE_TRANSITION;

public final class DevicePresenceListener implements IDevicePresenceListener
{
    private final String transportId;
    private final IUnicastInternal unicast;
    private final PulseManager pulseManager;
    private final RockLog rockLog;

    public DevicePresenceListener(String transportId, IUnicastInternal unicast, PulseManager pulseManager, RockLog rockLog)
    {
        this.transportId = transportId;
        this.unicast = unicast;
        this.pulseManager = pulseManager;
        this.rockLog = rockLog;
    }

    @Override
    public void onDevicePresenceChanged(DID did, boolean isPotentiallyAvailable)
    {
        if (isPotentiallyAvailable) {
            boolean wasExistingPulse = pulseManager.stopPulse(did, false);
            if (wasExistingPulse) {
                // this indicates that the previous state change
                // from online -> offline did _not_ shut down the pulsing
                // subsystem. this means that some transport is not
                // hooked up properly.
                // errors like this can lead to subtle bugs in the long-term,
                // so it's important that we're notified of them
                rockLog.newDefect(DEFECT_NAME_PULSE_SURVIVED_PRESENCE_TRANSITION)
                        .addData("transport", transportId)
                        .send();
            }
        } else {
            pulseManager.stopPulse(did, false);
            unicast.disconnect(did, new ExDeviceUnavailable("remote offline"));
        }
    }
}

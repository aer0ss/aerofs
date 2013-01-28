/**
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.daemon.core.device.DevicePresence;
import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.EIPulseStopped;
import com.aerofs.lib.event.Prio;
import com.google.inject.Inject;

/**
 * Event handler that handles {@link com.aerofs.daemon.event.net.EIPulseStopped}
 * events. Runs when the continuous pulses scheduled by the core stop
 */
public class HdPulseStopped implements IEventHandler<EIPulseStopped>
{
    private final DevicePresence _dp;

    @Inject
    public HdPulseStopped(DevicePresence dp)
    {
        _dp = dp;
    }

    @Override
    public void handle_(EIPulseStopped ev, Prio prio)
    {
        _dp.pulseStopped_(ev._tp, ev._did);
    }
}

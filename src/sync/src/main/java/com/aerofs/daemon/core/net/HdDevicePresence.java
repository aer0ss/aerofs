package com.aerofs.daemon.core.net;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.net.device.Devices;
import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.EIDevicePresence;
import com.aerofs.ids.DID;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import org.slf4j.Logger;

public class HdDevicePresence implements IEventHandler<EIDevicePresence>
{
    private static final Logger l = Loggers.getLogger(HdDevicePresence.class);

    private final Devices _devices;
    private final CfgLocalDID _cfgLocalDID;

    @Inject
    public HdDevicePresence(Devices devices, CfgLocalDID cfgLocalDID)
    {
        _devices = devices;
        _cfgLocalDID = cfgLocalDID;
    }

    @Override
    public void handle_(EIDevicePresence ev)
    {
        DID did = ev._did;
        Preconditions.checkArgument(!did.equals(_cfgLocalDID.get()));

        l.debug("{} {} {}", did, ev._online ? "+" : "-", ev._tp);

        if (ev._online) {
            _devices.online_(did, ev._tp);
        } else {
            _devices.offline_(did, ev._tp);
        }
    }
}

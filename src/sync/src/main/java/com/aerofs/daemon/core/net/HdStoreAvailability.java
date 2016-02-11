package com.aerofs.daemon.core.net;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.net.device.Devices;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.EIStoreAvailability;
import com.aerofs.ids.DID;
import com.aerofs.ids.SID;
import com.aerofs.lib.cfg.CfgLocalDID;
import com.aerofs.lib.id.SIndex;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;

import static com.google.common.base.Preconditions.checkState;

public class HdStoreAvailability implements IEventHandler<EIStoreAvailability>
{
    private static final Logger l = Loggers.getLogger(HdStoreAvailability.class);

    private final Devices _devices;
    private final IMapSID2SIndex _sid2sidx;
    private final CfgLocalDID _cfgLocalDID;

    @Inject
    public HdStoreAvailability(Devices devices, IMapSID2SIndex sid2sidx, CfgLocalDID cfgLocalDID)
    {
        _devices = devices;
        _sid2sidx = sid2sidx;
        _cfgLocalDID = cfgLocalDID;
    }

    @Override
    public void handle_(EIStoreAvailability ev)
    {
        Preconditions.checkArgument(!ev._did.equals(_cfgLocalDID.get()));

        SIndex sidx = _sid2sidx.getNullable_(ev._sid);
        if (sidx == null) return;

        l.debug("{} {} {}", ev._did, ev._join ? "+" : "-", ev._sid);

        if (ev._join) {
            _devices.join_(ev._did, sidx);
        } else {
            _devices.leave_(ev._did, sidx);
        }
    }
}

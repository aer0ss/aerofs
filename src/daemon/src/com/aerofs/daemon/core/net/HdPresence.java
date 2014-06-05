package com.aerofs.daemon.core.net;

import com.aerofs.base.Loggers;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.net.device.DevicePresence;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.EIPresence;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.id.SIndex;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;

public class HdPresence implements IEventHandler<EIPresence>
{
    private static final Logger l = Loggers.getLogger(HdPresence.class);

    private final DevicePresence _dp;
    private final IMapSID2SIndex _sid2sidx;

    @Inject
    public HdPresence(DevicePresence dp, IMapSID2SIndex sid2sidx)
    {
        _dp = dp;
        _sid2sidx = sid2sidx;
    }

    @Override
    public void handle_(EIPresence ev, Prio prio)
    {
        if (ev._did2sids.isEmpty()) {
            Preconditions.checkArgument(!ev._online);
            _dp.offline_(ev._tp);
        } else {
            for (Entry<DID, Collection<SID>> entry : ev._did2sids.entrySet()) {
                DID did = entry.getKey();
                Collection<SID> sids = entry.getValue();

                Preconditions.checkArgument(!did.equals(Cfg.did()));

                List<SIndex> sidcs = Lists.newArrayListWithCapacity(sids.size());
                for (SID sid : sids) {
                    SIndex sidx = _sid2sidx.getNullable_(sid);

                    // ignore stores that don't exist
                    if (sidx != null) {
                        sidcs.add(sidx);
                    }
                }

                l.debug("{} {} {}", entry.getKey(), ev._online ? "+" : "-", entry.getValue());

                if (ev._online) {
                    _dp.online_(ev._tp, did, sidcs);
                } else {
                    _dp.offline_(ev._tp, did, sidcs);
                }
            }
        }
    }
}

package com.aerofs.daemon.core.net;

import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;

import com.aerofs.daemon.core.device.DevicePresence;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.EIPresence;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.lib.id.DID;
import com.aerofs.lib.id.SID;
import com.aerofs.lib.id.SIndex;
import com.google.common.collect.Lists;
import com.google.inject.Inject;

public class HdPresence implements IEventHandler<EIPresence>
{
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
        if (ev._did2sids == null) {
            assert !ev._online;
            _dp.offline_(ev._tp);

        } else {
            for (Entry<DID, Collection<SID>> en : ev._did2sids.entrySet()) {
                DID did = en.getKey();
                Collection<SID> sids = en.getValue();
                assert !did.equals(Cfg.did());

                List<SIndex> sidcs = Lists.newArrayListWithCapacity(sids.size());
                for (SID sid : sids) {
                    SIndex sidx = _sid2sidx.getNullable_(sid);
                    // ignore stores that don't exist
                    if (sidx != null) sidcs.add(sidx);
                }
                Util.l(this).debug("sids " + sids + " sidcs " + sidcs);

                if (ev._online) _dp.online_(ev._tp, did, sidcs);
                else _dp.offline_(ev._tp, did, sidcs);
            }
        }
    }
}

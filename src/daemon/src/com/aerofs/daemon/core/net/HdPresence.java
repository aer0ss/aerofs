package com.aerofs.daemon.core.net;

import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;

import com.aerofs.daemon.core.device.DevicePresence;
import com.aerofs.daemon.core.store.IMapSID2SIndex;
import com.aerofs.daemon.event.IEventHandler;
import com.aerofs.daemon.event.net.EIPresence;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.base.id.DID;
import com.aerofs.base.id.SID;
import com.aerofs.lib.id.SIndex;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import org.slf4j.Logger;

public class HdPresence implements IEventHandler<EIPresence>
{
    private final DevicePresence _dp;
    private final IMapSID2SIndex _sid2sidx;
    private static final Logger l = Util.l(HdPresence.class);

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
            assert !ev._online;
            _dp.offline_(ev._tp);

        } else {
            if (l.isDebugEnabled()) l.debug("did2sids " + ev._did2sids);

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
                if (l.isDebugEnabled()) l.debug(" sidcs " + sidcs);

                if (ev._online) _dp.online_(ev._tp, did, sidcs);
                else _dp.offline_(ev._tp, did, sidcs);
            }
        }
    }
}

/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.fs;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.StoreHierarchy;
import com.aerofs.daemon.event.admin.EIListUserRoots;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.id.SIndex;
import com.google.common.collect.Maps;
import com.google.inject.Inject;

import java.util.Collection;
import java.util.Map;

public class HdListUserRoots extends AbstractHdIMC<EIListUserRoots>
{
    private final StoreHierarchy _ss;
    private final IMapSIndex2SID _sidx2sid;

    @Inject
    public HdListUserRoots(StoreHierarchy ss, IMapSIndex2SID sidx2sid)
    {
        _ss = ss;
        _sidx2sid = sidx2sid;
    }

    @Override
    protected void handleThrows_(EIListUserRoots ev, Prio prio) throws Exception
    {
        Collection<SIndex> all = _ss.getAll_();
        Map<SID, String> userRoots = Maps.newHashMap();
        for (SIndex sidx : all) {

            if (!_sidx2sid.get_(sidx).isUserRoot()) continue;
            userRoots.put(new SID(BaseUtil.fromPB(BaseUtil.toPB(_sidx2sid.get_(sidx)))), _ss.getName_(sidx));
        }

        ev.setResult_(userRoots);
    }
}

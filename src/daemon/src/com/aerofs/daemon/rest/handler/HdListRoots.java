package com.aerofs.daemon.rest.handler;

import com.aerofs.base.ex.ExNoPerm;
import com.aerofs.base.id.OID;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.rest.RestObject;
import com.aerofs.daemon.rest.event.EIListRoots;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.id.SIndex;
import com.aerofs.rest.api.File;
import com.aerofs.rest.api.Folder;
import com.aerofs.rest.api.Listing;
import com.google.common.collect.Lists;
import com.google.inject.Inject;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class HdListRoots extends AbstractHdIMC<EIListRoots>
{
    private final IStores _ss;
    private final IMapSIndex2SID _sidx2sid;
    private final CfgLocalUser _localUser;

    @Inject
    public HdListRoots(CfgLocalUser localUser, IMapSIndex2SID sidx2sid, IStores ss)
    {
        _ss = ss;
        _sidx2sid = sidx2sid;
        _localUser = localUser;
    }

    @Override
    protected void handleThrows_(EIListRoots ev, Prio prio) throws ExNoPerm, SQLException
    {
        // TODO: support TS
        if (!ev._user.equals(_localUser.get())) throw new ExNoPerm();

        Collection<SIndex> all = _ss.getAll_();
        List<Folder> roots = Lists.newArrayListWithCapacity(all.size());
        for (SIndex sidx : all) {
            if (_ss.isRoot_(sidx)) {
                roots.add(new Folder(_ss.getName_(sidx),
                        new RestObject(_sidx2sid.get_(sidx), OID.ROOT).toStringFormal(), true));
            }
        }

        ev.setResult_(new Listing(roots, Collections.<File>emptyList()));
    }
}

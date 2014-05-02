/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.core.fs;

import com.aerofs.base.id.OID;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.event.admin.EIListUserRoots;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.aerofs.proto.Ritual.PBSharedFolder;
import com.google.common.collect.Lists;
import com.google.inject.Inject;

import java.util.Collection;

public class HdListUserRoots extends AbstractHdIMC<EIListUserRoots>
{
    private final IStores _ss;
    private final IMapSIndex2SID _sidx2sid;
    private final DirectoryService _ds;

    @Inject
    public HdListUserRoots(IStores ss, DirectoryService ds, IMapSIndex2SID sidx2sid)
    {
        _ds = ds;
        _ss = ss;
        _sidx2sid = sidx2sid;
    }

    @Override
    protected void handleThrows_(EIListUserRoots ev, Prio prio) throws Exception
    {
        Collection<SIndex> all = _ss.getAll_();
        Collection<PBSharedFolder> sharedFolders = Lists.newArrayListWithCapacity(all.size());
        for (SIndex sidx : all) {

            if (!_sidx2sid.get_(sidx).isUserRoot()) continue;
            sharedFolders.add(PBSharedFolder.newBuilder()
                    .setStoreId(_sidx2sid.get_(sidx).toPB())
                    .setName(_ss.getName_(sidx))
                    .setPath(_ds.resolve_(new SOID(sidx, OID.ROOT)).toPB())
                    .setAdmittedOrLinked(true)
                    .build());
        }

        ev.setResult_(sharedFolders);
    }
}

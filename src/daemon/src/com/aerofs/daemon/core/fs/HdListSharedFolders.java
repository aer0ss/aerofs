package com.aerofs.daemon.core.fs;

import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.store.IMapSIndex2SID;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.event.admin.EIListSharedFolders;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.Path;
import com.aerofs.base.id.OID;
import com.aerofs.lib.id.SIndex;
import com.aerofs.lib.id.SOID;
import com.google.common.collect.Lists;
import com.google.inject.Inject;

import java.util.Collection;

public class HdListSharedFolders extends AbstractHdIMC<EIListSharedFolders>
{
    private final IStores _ss;
    private final IMapSIndex2SID _sidx2sid;
    private final DirectoryService _ds;

    @Inject
    public HdListSharedFolders(IStores ss, DirectoryService ds, IMapSIndex2SID sidx2sid)
    {
        _ds = ds;
        _ss = ss;
        _sidx2sid = sidx2sid;
    }

    @Override
    protected void handleThrows_(EIListSharedFolders ev, Prio prio) throws Exception
    {
        Collection<SIndex> all = _ss.getAll_();
        Collection<Path> paths = Lists.newArrayListWithCapacity(all.size());
        for (SIndex sidx : all) {
            if (!_sidx2sid.get_(sidx).isUserRoot()) {
                paths.add(_ds.resolve_(new SOID(sidx, OID.ROOT)));
            }
        }

        ev.setResult_(paths);
    }
}

/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.core.first.SeedCreator;
import com.aerofs.daemon.core.multiplicity.singleuser.SingleuserStores;
import com.aerofs.daemon.core.store.IStores;
import com.aerofs.daemon.event.admin.EICreateSeedFile;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.labeling.L;
import com.aerofs.lib.event.Prio;
import com.google.inject.Inject;

public class HdCreateSeedFile extends AbstractHdIMC<EICreateSeedFile>
{
    private final IStores _stores;
    private final SeedCreator _sc;

    @Inject
    public HdCreateSeedFile(SeedCreator sc, IStores stores)
    {
        _sc = sc;
        _stores = stores;
    }

    @Override
    protected void handleThrows_(EICreateSeedFile ev, Prio prio) throws Exception
    {
        if (L.get().isMultiuser()) {
            // TODO ?
        } else {
            ev.setResult_(_sc.create_(((SingleuserStores)_stores).getUserRoot_()));
        }
    }
}

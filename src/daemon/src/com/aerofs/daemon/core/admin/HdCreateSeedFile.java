/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.core.first.SeedCreator;
import com.aerofs.daemon.event.admin.EICreateSeedFile;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.event.Prio;
import com.google.inject.Inject;

public class HdCreateSeedFile extends AbstractHdIMC<EICreateSeedFile>
{
    private final SeedCreator _sc;

    @Inject
    public HdCreateSeedFile(SeedCreator sc)
    {
        _sc = sc;
    }

    @Override
    protected void handleThrows_(EICreateSeedFile ev, Prio prio) throws Exception
    {
        ev.setResult_(_sc.create_());
    }
}

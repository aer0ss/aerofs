/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.admin;

import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.daemon.core.first_launch.SeedCreator;
import com.aerofs.daemon.event.admin.EICreateSeedFile;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.Util;
import com.aerofs.lib.cfg.Cfg;
import com.google.inject.Inject;

import static com.aerofs.lib.ClientParam.seedFileName;

public class HdCreateSeedFile extends AbstractHdIMC<EICreateSeedFile>
{
    private final SeedCreator _sc;

    @Inject
    public HdCreateSeedFile(SeedCreator sc)
    {
        _sc = sc;
    }

    @Override
    protected void handleThrows_(EICreateSeedFile ev) throws Exception
    {
        String absRoot = Cfg.getRootPathNullable(ev._sid);
        if (absRoot == null) throw new ExBadArgs();
        
        String path = Util.join(absRoot, seedFileName(ev._sid));
        _sc.create_(ev._sid, path);

        ev.setResult_(path);
    }
}

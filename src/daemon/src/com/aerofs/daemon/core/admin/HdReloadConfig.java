package com.aerofs.daemon.core.admin;

import com.aerofs.base.ex.ExBadArgs;
import com.aerofs.daemon.event.admin.EIReloadConfig;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.cfg.Cfg;

public class HdReloadConfig extends AbstractHdIMC<EIReloadConfig>
{
    @Override
    protected void handleThrows_(EIReloadConfig ev) throws Exception
    {
        String old = Cfg.absDefaultRootAnchor();

        Cfg.init_(Cfg.absRTRoot(), false);

        // moving of root anchor is done in HdRelocateRootAnchor
        if (!old.equals(Cfg.absDefaultRootAnchor())) {
            throw new ExBadArgs("Root anchor must be changed explictly");
        }
    }
}

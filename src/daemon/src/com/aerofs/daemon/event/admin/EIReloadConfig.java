package com.aerofs.daemon.event.admin;

import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;

public class EIReloadConfig extends AbstractEBIMC
{
    public EIReloadConfig()
    {
        super(Core.imce());
    }
}

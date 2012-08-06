package com.aerofs.daemon.event.admin;

import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.lib.Path;
import com.google.common.collect.ImmutableList;

public class EIListExpelledObjects extends AbstractEBIMC
{
    public ImmutableList<Path> _expelledObjects;

    public EIListExpelledObjects()
    {
        super(Core.imce());
    }

    public void setResult_(ImmutableList<Path> expelledObjects)
    {
        _expelledObjects = expelledObjects;
    }
}

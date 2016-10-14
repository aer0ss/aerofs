/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.event.fs;

import com.aerofs.ids.SID;
import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;

/**
 * NB: although this event is not located in phy.linker to be accessible from Ritual, it is only
 * handled by LinkedStorage
 */
public class EICreateRoot extends AbstractEBIMC
{
    public final String _path;
    public SID _sid;

    public EICreateRoot(String path)
    {
        super(Core.imce());
        _path = path;
    }

    public void setResult(SID sid)
    {
        _sid = sid;
    }

    public SID sid()
    {
        return _sid;
    }
}

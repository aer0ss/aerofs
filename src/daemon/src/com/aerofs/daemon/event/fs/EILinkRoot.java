/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.event.fs;

import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;

import javax.annotation.Nullable;

/**
 * NB: although this event is not located in phy.linker to be accessible from Ritual, it is only
 * handled by LinkedStorage
 */
public class EILinkRoot extends AbstractEBIMC
{
    public final String _path;
    public @Nullable SID _sid;

    public EILinkRoot(String path, @Nullable SID sid)
    {
        super(Core.imce());
        _path = path;
        _sid = sid;
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

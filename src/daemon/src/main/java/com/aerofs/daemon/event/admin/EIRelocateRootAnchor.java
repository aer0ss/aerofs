package com.aerofs.daemon.event.admin;

import com.aerofs.ids.SID;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;

import javax.annotation.Nullable;

public class EIRelocateRootAnchor extends AbstractEBIMC
{
    public final @Nullable SID _sid;
    public final String _newRootAnchor;

    public EIRelocateRootAnchor(String newRootAnchor, @Nullable SID sid, IIMCExecutor imce)
    {
        super(imce);
        _sid = sid;
        _newRootAnchor = newRootAnchor;
    }
}

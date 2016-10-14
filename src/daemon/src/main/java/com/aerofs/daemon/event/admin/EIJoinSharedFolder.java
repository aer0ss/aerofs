package com.aerofs.daemon.event.admin;

import com.aerofs.ids.SID;
import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;

public class EIJoinSharedFolder extends AbstractEBIMC
{
    public final SID _sid;

    public EIJoinSharedFolder(SID sid)
    {
        super(Core.imce());
        _sid = sid;
    }
}

package com.aerofs.daemon.event.fs;

import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.lib.Path;

public class EIUnlinkRoot extends AbstractEBIMC
{
    public final SID _sid;

    /**
     * Set an external folder to be a pending root.
     */
    public EIUnlinkRoot(SID sid)
    {
        super(Core.imce());
        _sid = sid;
    }
}

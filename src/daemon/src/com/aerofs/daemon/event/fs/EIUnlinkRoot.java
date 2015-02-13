package com.aerofs.daemon.event.fs;

import com.aerofs.ids.SID;
import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;

public class EIUnlinkRoot extends AbstractEBIMC
{
    public final SID _sid;

    /**
     * Set an external folder to be a unlinked root.
     */
    public EIUnlinkRoot(SID sid)
    {
        super(Core.imce());
        _sid = sid;
    }
}

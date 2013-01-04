package com.aerofs.daemon.event.admin;

import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.event.fs.AbstractEIFS;
import com.aerofs.lib.cfg.Cfg;

public class EIJoinSharedFolder extends AbstractEIFS
{
    public final SID _sid;

    public EIJoinSharedFolder(SID sid)
    {
        super(Cfg.user(), Core.imce());
        _sid = sid;
    }
}

package com.aerofs.daemon.event.admin;

import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.event.fs.AbstractEIFS;
import com.aerofs.lib.cfg.Cfg;

public class EIJoinSharedFolder extends AbstractEIFS
{
    public final String _code;

    public EIJoinSharedFolder(String code)
    {
        super(Cfg.user(), Core.imce());
        _code = code;
    }
}

package com.aerofs.daemon.event.fs;

import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.id.SOKID;

// TODO should only support non-meta CIDs

public class EIBeginRead extends AbstractEIFS
{
    public final SOKID _sokid;

    public EIBeginRead(String user, SOKID sokid, IIMCExecutor imce)
    {
        super(user, imce);
        _sokid = sokid;
    }
}

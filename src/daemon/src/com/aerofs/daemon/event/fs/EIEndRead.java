package com.aerofs.daemon.event.fs;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.id.SOKID;

public class EIEndRead extends AbstractEBIMC
{

    private final SOKID _sokid;

    public EIEndRead(SOKID sokid, IIMCExecutor imce)
    {
        super(imce);
        _sokid = sokid;
    }

    public SOKID sokid()
    {
        return _sokid;
    }
}

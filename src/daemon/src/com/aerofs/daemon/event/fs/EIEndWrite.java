package com.aerofs.daemon.event.fs;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.id.SOID;

public class EIEndWrite extends AbstractEBIMC
{
    public final SOID _soid;

    public EIEndWrite(SOID soid, IIMCExecutor imce)
    {
        super(imce);
        _soid = soid;
    }
}

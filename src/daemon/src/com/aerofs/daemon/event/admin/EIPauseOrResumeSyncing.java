package com.aerofs.daemon.event.admin;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;

public class EIPauseOrResumeSyncing extends AbstractEBIMC
{
    public final boolean _pause;

    public EIPauseOrResumeSyncing(boolean pause, IIMCExecutor imce)
    {
        super(imce);
        _pause = pause;
    }
}

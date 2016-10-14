package com.aerofs.daemon.core.phy.linked.linker.event;

import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;

public class EITestPauseOrResumeLinker extends AbstractEBIMC
{
    public final boolean _pause;

    public EITestPauseOrResumeLinker(boolean pause)
    {
        super(Core.imce());

        _pause = pause;
    }
}

/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.net;

import com.aerofs.daemon.event.admin.EIGetTransferStat;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.event.Prio;
import com.google.inject.Inject;

public class HdGetTransferStat extends AbstractHdIMC<EIGetTransferStat>
{
    private final Transports _ts;

    @Inject
    public HdGetTransferStat(Transports ts)
    {
        _ts = ts;
    }

    @Override
    protected void handleThrows_(EIGetTransferStat ev, Prio prio)
            throws Exception
    {
        ev._ts = _ts;
    }
}

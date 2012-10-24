/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.event.admin.EIHeartbeat;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.Prio;

public class HdHeartbeat extends AbstractHdIMC<EIHeartbeat>
{

    @Override
    // This method simply contacts the core and does nothing
    protected void handleThrows_(EIHeartbeat ev, Prio prio) throws Exception
    {

    }
}

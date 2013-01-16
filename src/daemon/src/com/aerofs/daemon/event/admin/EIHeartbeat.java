/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.event.admin;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;

public class EIHeartbeat extends AbstractEBIMC
{
    public EIHeartbeat(IIMCExecutor imce)
    {
        super(imce);
    }
}

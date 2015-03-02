/**
 * Created by Allen A. George, Air Computing Inc.
 * Copyright (c) Air Computing Inc., 2011.
 */

package com.aerofs.daemon.event.net;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;

public class EOKill extends AbstractEBIMC
{
    public EOKill(Endpoint ep, IIMCExecutor imce)
    {
        super(imce);

        //_ep = ep;
    }

    //private Endpoint _ep;
}

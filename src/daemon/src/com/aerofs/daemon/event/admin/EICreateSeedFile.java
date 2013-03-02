/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.event.admin;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;

public class EICreateSeedFile extends AbstractEBIMC
{
    public String _path;

    public EICreateSeedFile(IIMCExecutor imce)
    {
        super(imce);
    }

    public void setResult_(String path)
    {
        _path = path;
    }
}

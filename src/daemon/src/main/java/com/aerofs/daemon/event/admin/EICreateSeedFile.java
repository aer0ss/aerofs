/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.event.admin;

import com.aerofs.ids.SID;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;

public class EICreateSeedFile extends AbstractEBIMC
{
    public final SID _sid;
    public String _path;

    public EICreateSeedFile(SID sid, IIMCExecutor imce)
    {
        super(imce);
        _sid = sid;
    }

    public void setResult_(String path)
    {
        _path = path;
    }
}

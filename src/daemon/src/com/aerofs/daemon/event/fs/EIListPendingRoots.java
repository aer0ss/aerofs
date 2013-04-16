/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.event.fs;

import com.aerofs.base.id.SID;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;

import java.util.Map;

public class EIListPendingRoots extends AbstractEBIMC
{
    private Map<SID, String> _pending;

    public EIListPendingRoots(IIMCExecutor imce)
    {
        super(imce);
    }

    public void setResult(Map<SID, String> pending)
    {
        _pending = pending;
    }

    public Map<SID, String> pending()
    {
        return _pending;
    }
}

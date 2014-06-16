/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.event.fs;

import com.aerofs.base.id.SID;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;

import java.util.Map;

public class EIListUnlinkedRoots extends AbstractEBIMC
{
    private Map<SID, String> _unlinked;

    public EIListUnlinkedRoots(IIMCExecutor imce)
    {
        super(imce);
    }

    public void setResult(Map<SID, String> unlinked)
    {
        _unlinked = unlinked;
    }

    public Map<SID, String> unlinked()
    {
        return _unlinked;
    }
}

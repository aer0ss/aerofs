/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.event.admin;

import com.aerofs.ids.SID;
import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;

import java.util.Map;

public class EIListUserRoots extends AbstractEBIMC
{
    private Map<SID, String> _userRoots;

    public EIListUserRoots()
    {
        super(Core.imce());
    }

    public void setResult_(Map<SID, String> userRoots)
    {
        _userRoots = userRoots;
    }

    public Map<SID, String> getUserRoots()
    {
        return _userRoots;
    }
}

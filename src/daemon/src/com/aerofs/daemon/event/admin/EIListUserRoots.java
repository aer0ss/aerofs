/*
 * Copyright (c) Air Computing Inc., 2014.
 */

package com.aerofs.daemon.event.admin;

import java.util.Collection;

import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.proto.Ritual.PBSharedFolder;

public class EIListUserRoots extends AbstractEBIMC
{
    public Collection<PBSharedFolder> _userRoots;

    public EIListUserRoots()
    {
        super(Core.imce());
    }

    public void setResult_(Collection<PBSharedFolder> userRoots)
    {
        _userRoots = userRoots;
    }
}

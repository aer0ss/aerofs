/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.mobile;

import com.aerofs.base.id.UserID;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;

import java.util.List;

public class EIGetChildrenAttr extends AbstractEBIMC
{
    public final UserID _user;
    public final Path _path;
    public List<OA> _oas;

    public EIGetChildrenAttr(UserID user, Path path, IIMCExecutor imce)
    {
        super(imce);
        _user = user;
        _path = path;
    }

    public void setResult_(List<OA> oas)
    {
        _oas = oas;
    }
}

/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.event.admin;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;

public class EITestMultiuserJoinRootStore extends AbstractEBIMC
{
    public final String _user;

    public EITestMultiuserJoinRootStore(String user, IIMCExecutor imce)
    {
        super(imce);
        _user = user;
    }
}

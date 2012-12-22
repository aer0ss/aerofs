/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.daemon.event.admin;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.base.id.UserID;

public class EITestMultiuserJoinRootStore extends AbstractEBIMC
{
    public final UserID _user;

    public EITestMultiuserJoinRootStore(UserID user, IIMCExecutor imce)
    {
        super(imce);
        _user = user;
    }
}

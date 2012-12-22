package com.aerofs.daemon.event.admin;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;
import com.aerofs.base.id.UserID;

public class EIDeleteACL extends AbstractEBIMC
{
    public final UserID _user;
    public final Path _path;
    public final Iterable<UserID> _subjects;

    public EIDeleteACL(UserID user, Path path, Iterable<UserID> subjects, IIMCExecutor imce)
    {
        super(imce);
        _path = path;
        _user = user;
        _subjects = subjects;
    }
}

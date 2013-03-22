package com.aerofs.daemon.event.admin;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;
import com.aerofs.lib.acl.Role;
import com.aerofs.base.id.UserID;

public class EIUpdateACL extends AbstractEBIMC
{
    public final UserID _user;
    public final Path _path;
    public final UserID _subject;
    public final Role _role;

    public EIUpdateACL(UserID user, Path path, UserID subject, Role role, IIMCExecutor imce)
    {
        super(imce);
        _path = path;
        _user = user;
        _subject = subject;
        _role = role;
    }
}

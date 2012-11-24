package com.aerofs.daemon.event.admin;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;
import com.aerofs.lib.acl.Role;
import com.aerofs.lib.id.UserID;
import com.google.common.collect.ImmutableMap;

public class EIGetACL extends AbstractEBIMC
{
    public final UserID _user;
    public final Path _path;

    // result value
    public ImmutableMap<UserID, Role> _subject2role;

    public EIGetACL(UserID user, Path path, IIMCExecutor imce)
    {
        super(imce);
        _path = path;
        _user = user;
    }

    public void setResult_(ImmutableMap<UserID, Role> subject2role)
    {
        _subject2role = subject2role;
    }
}

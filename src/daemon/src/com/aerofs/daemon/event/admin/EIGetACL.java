package com.aerofs.daemon.event.admin;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;
import com.aerofs.lib.Role;
import com.google.common.collect.ImmutableMap;

public class EIGetACL extends AbstractEBIMC
{
    public final String _user;
    public final Path _path;

    // result value
    public ImmutableMap<String, Role> _subject2role;

    public EIGetACL(String user, Path path, IIMCExecutor imce)
    {
        super(imce);
        this._path = path;
        this._user = user;
    }

    public void setResult_(ImmutableMap<String, Role> subject2role)
    {
        _subject2role = subject2role;
    }
}

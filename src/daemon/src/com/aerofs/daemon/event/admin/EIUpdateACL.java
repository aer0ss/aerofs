package com.aerofs.daemon.event.admin;

import java.util.Map;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;
import com.aerofs.lib.acl.Role;

public class EIUpdateACL extends AbstractEBIMC
{
    public final String _user;
    public final Path _path;
    public final Map<String, Role> _subject2role;

    public EIUpdateACL(String user, Path path, Map<String, Role> subject2role, IIMCExecutor imce)
    {
        super(imce);
        _path = path;
        _user = user;
        _subject2role = subject2role;
    }
}

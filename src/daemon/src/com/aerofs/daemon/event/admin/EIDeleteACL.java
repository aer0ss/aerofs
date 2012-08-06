package com.aerofs.daemon.event.admin;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;

public class EIDeleteACL extends AbstractEBIMC
{
    public final String _user;
    public final Path _path;
    public final Iterable<String> _subjects;

    public EIDeleteACL(String user, Path path, Iterable<String> subjects, IIMCExecutor imce)
    {
        super(imce);
        _path = path;
        _user = user;
        _subjects = subjects;
    }
}

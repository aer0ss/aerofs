package com.aerofs.daemon.event.admin;

import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;
import com.aerofs.ids.UserID;

public class EIDeleteACL extends AbstractEBIMC
{
    public final Path _path;
    public final UserID _subject;

    public EIDeleteACL(Path path, UserID subject, IIMCExecutor imce)
    {
        super(imce);
        _path = path;
        _subject = subject;
    }
}

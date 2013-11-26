package com.aerofs.daemon.event.admin;

import com.aerofs.base.acl.Permissions;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.daemon.event.lib.imc.IIMCExecutor;
import com.aerofs.lib.Path;
import com.aerofs.base.id.UserID;

public class EIUpdateACL extends AbstractEBIMC
{
    public final Path _path;
    public final UserID _subject;
    public final Permissions _permissions;
    public final boolean _suppressSharedFolderRulesWarnings;

    public EIUpdateACL(Path path, UserID subject, Permissions permissions, IIMCExecutor imce,
            boolean suppressSharedFolderRulesWarnings)
    {
        super(imce);
        _path = path;
        _subject = subject;
        _permissions = permissions;
        _suppressSharedFolderRulesWarnings = suppressSharedFolderRulesWarnings;
    }
}

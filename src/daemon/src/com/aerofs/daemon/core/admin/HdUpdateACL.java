package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.core.acl.ACLSynchronizer;
import com.aerofs.daemon.event.admin.EIUpdateACL;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.google.inject.Inject;

public class HdUpdateACL extends AbstractHdIMC<EIUpdateACL>
{
    private final ACLSynchronizer _aclsync;
    private final Path2SIndexResolver _sru;

    @Inject
    public HdUpdateACL(ACLSynchronizer aclsync, Path2SIndexResolver sru)
    {
        _aclsync = aclsync;
        _sru = sru;
    }

    @Override
    protected void handleThrows_(EIUpdateACL ev)
            throws Exception
    {
        l.info("Updating acl for store with path: {}", ev._path);
        _aclsync.update_(_sru.getSIndex_(ev._path), ev._subject, ev._permissions,
                ev._suppressSharedFolderRulesWarnings);
        l.info("Updated acl for store with path: {}", ev._path);
    }
}
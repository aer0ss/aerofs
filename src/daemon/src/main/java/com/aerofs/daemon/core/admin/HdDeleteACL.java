package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.core.acl.ACLSynchronizer;
import com.aerofs.daemon.event.admin.EIDeleteACL;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.google.inject.Inject;

public class HdDeleteACL extends AbstractHdIMC<EIDeleteACL>
{
    private final ACLSynchronizer _aclsync;
    private final Path2SIndexResolver _sru;

    @Inject
    public HdDeleteACL(ACLSynchronizer aclsync, Path2SIndexResolver sru)
    {
        _aclsync = aclsync;
        _sru = sru;
    }

    @Override
    protected void handleThrows_(EIDeleteACL ev)
            throws Exception
    {
        l.info("Deleting acl for store with path: {}", ev._path);
        _aclsync.delete_(_sru.getSIndex_(ev._path), ev._subject);
        l.info("Deleted acl for store with path: {}", ev._path);
    }
}
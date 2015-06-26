package com.aerofs.daemon.core.admin;

import com.aerofs.base.analytics.Analytics;
import com.aerofs.base.analytics.AnalyticsEvents.SimpleEvents;
import com.aerofs.daemon.core.acl.ACLSynchronizer;
import com.aerofs.daemon.event.admin.EIDeleteACL;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.google.inject.Inject;

public class HdDeleteACL extends AbstractHdIMC<EIDeleteACL>
{
    private final ACLSynchronizer _aclsync;
    private final Analytics _analytics;
    private final Path2SIndexResolver _sru;

    @Inject
    public HdDeleteACL(ACLSynchronizer aclsync, Analytics analytics, Path2SIndexResolver sru)
    {
        _aclsync = aclsync;
        _analytics = analytics;
        _sru = sru;
    }

    @Override
    protected void handleThrows_(EIDeleteACL ev)
            throws Exception
    {
        l.info("Deleting acl for store with path: {}", ev._path);
        _analytics.track(SimpleEvents.KICKOUT);
        _aclsync.delete_(_sru.getSIndex_(ev._path), ev._subject);
        l.info("Deleted acl for store with path: {}", ev._path);
    }
}
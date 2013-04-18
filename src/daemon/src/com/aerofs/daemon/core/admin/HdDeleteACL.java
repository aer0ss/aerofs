package com.aerofs.daemon.core.admin;

import com.aerofs.base.analytics.Analytics;
import com.aerofs.base.analytics.AnalyticsEvents.SimpleEvents;
import com.aerofs.daemon.core.acl.ACLChecker;
import com.aerofs.daemon.core.acl.ACLSynchronizer;
import com.aerofs.daemon.event.admin.EIDeleteACL;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.event.Prio;
import com.aerofs.base.acl.Role;
import com.aerofs.daemon.core.ex.ExNotShared;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

public class HdDeleteACL extends AbstractHdIMC<EIDeleteACL>
{
    private final ACLChecker _acl;
    private final ACLSynchronizer _aclsync;
    private final Analytics _analytics;

    @Inject
    public HdDeleteACL(ACLChecker acl, ACLSynchronizer aclsync, Analytics analytics)
    {
        _acl = acl;
        _aclsync = aclsync;
        _analytics = analytics;
    }

    @Override
    protected void handleThrows_(EIDeleteACL ev, Prio prio)
            throws Exception
    {
        SOID soid = _acl.checkThrows_(ev._user, ev._path, Role.OWNER);
        if (!soid.oid().isRoot()) throw new ExNotShared();

        _analytics.track(SimpleEvents.KICKOUT);

        _aclsync.delete_(soid.sidx(), ev._subject);
    }
}

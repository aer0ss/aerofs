package com.aerofs.daemon.core.admin;

import com.aerofs.base.analytics.Analytics;
import com.aerofs.base.analytics.AnalyticsEvents.SimpleEvents;
import com.aerofs.daemon.core.acl.ACLSynchronizer;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.event.admin.EIDeleteACL;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.ex.ExNotShared;
import com.aerofs.lib.id.SOID;
import com.google.inject.Inject;

public class HdDeleteACL extends AbstractHdIMC<EIDeleteACL>
{
    private final DirectoryService _ds;
    private final ACLSynchronizer _aclsync;
    private final Analytics _analytics;

    @Inject
    public HdDeleteACL(DirectoryService ds, ACLSynchronizer aclsync, Analytics analytics)
    {
        _ds = ds;
        _aclsync = aclsync;
        _analytics = analytics;
    }

    @Override
    protected void handleThrows_(EIDeleteACL ev, Prio prio)
            throws Exception
    {
        SOID soid = _ds.resolveFollowAnchorThrows_(ev._path);
        if (!soid.oid().isRoot()) throw new ExNotShared();

        _analytics.track(SimpleEvents.KICKOUT);

        _aclsync.delete_(soid.sidx(), ev._subject);
    }
}

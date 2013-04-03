/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.fs;

import com.aerofs.base.BaseParam.SP;
import com.aerofs.base.Loggers;
import com.aerofs.base.id.SID;
import com.aerofs.daemon.core.acl.ACLChecker;
import com.aerofs.daemon.core.acl.ACLSynchronizer;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.event.admin.EILeaveSharedFolder;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.event.Prio;
import com.aerofs.base.acl.Role;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.daemon.core.ex.ExNotShared;
import com.aerofs.lib.id.SOID;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.sp.client.SPClientFactory;
import com.google.inject.Inject;
import org.slf4j.Logger;

public class HdLeaveSharedFolder extends AbstractHdIMC<EILeaveSharedFolder>
{
    private static final Logger l = Loggers.getLogger(HdLeaveSharedFolder.class);

    private final TC _tc;
    private final DirectoryService _ds;
    private final ACLChecker _acl;
    private final ACLSynchronizer _aclsync;

    @Inject
    public HdLeaveSharedFolder(TC tc, DirectoryService ds, ACLChecker acl, ACLSynchronizer aclsync)
    {
        _tc = tc;
        _ds = ds;
        _acl = acl;
        _aclsync = aclsync;
    }

    @Override
    protected void handleThrows_(EILeaveSharedFolder ev, Prio prio) throws Exception
    {
        SOID soid = _acl.checkNoFollowAnchorThrows_(Cfg.user(), ev._path, Role.EDITOR);
        OA oa = _ds.getOAThrows_(soid);
        if (!oa.isAnchor() || !soid.oid().isAnchor()) throw new ExNotShared();

        SID sid = SID.anchorOID2storeSID(soid.oid());

        l.info("leave: " + sid + " " + ev._path);

        Token tk = _tc.acquireThrows_(Cat.UNLIMITED, "sp-leave");
        TCB tcb = null;
        try {
            tcb = tk.pseudoPause_("sp-leave");
            // join the shared folder through SP
            SPBlockingClient sp = SPClientFactory.newBlockingClient(SP.URL, Cfg.user());
            sp.signInRemote();
            sp.leaveSharedFolder(sid.toPB());
        } finally {
            if (tcb != null) tcb.pseudoResumed_();
            tk.reclaim_();
        }

        /**
         * Force ACL sync to make sure we do not report success to the Ritual caller before the
         * ACL changes propagate back to us.
         */
        _aclsync.syncToLocal_();
    }
}

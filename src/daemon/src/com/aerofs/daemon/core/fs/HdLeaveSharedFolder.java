/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.fs;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.ids.SID;
import com.aerofs.daemon.core.acl.ACLSynchronizer;
import com.aerofs.daemon.core.ds.DirectoryService;
import com.aerofs.daemon.core.ds.OA;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.event.admin.EILeaveSharedFolder;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.ex.ExNotShared;
import com.aerofs.lib.id.SOID;
import com.aerofs.sp.client.InjectableSPBlockingClientFactory;
import com.aerofs.sp.client.SPBlockingClient;
import com.google.inject.Inject;
import org.slf4j.Logger;

public class HdLeaveSharedFolder extends AbstractHdIMC<EILeaveSharedFolder>
{
    private static final Logger l = Loggers.getLogger(HdLeaveSharedFolder.class);

    private final TokenManager _tokenManager;
    private final DirectoryService _ds;
    private final ACLSynchronizer _aclsync;
    private final SPBlockingClient.Factory _factSP;

    @Inject
    public HdLeaveSharedFolder(TokenManager tokenManager, DirectoryService ds,
            ACLSynchronizer aclsync, InjectableSPBlockingClientFactory factSP)
    {
        _tokenManager = tokenManager;
        _ds = ds;
        _aclsync = aclsync;
        _factSP = factSP;
    }

    @Override
    protected void handleThrows_(EILeaveSharedFolder ev, Prio prio) throws Exception
    {
        SID sid;
        if (ev._path.isEmpty() && !ev._path.sid().isUserRoot()) {
            // root of an external shared folder -> leave the external shared folder
            sid = ev._path.sid();
        } else {
            // anywhere else: look for an anchor to leave
            SOID soid = _ds.resolveThrows_(ev._path);
            OA oa = _ds.getOAThrows_(soid);
            if (!oa.isAnchor() || !soid.oid().isAnchor()) throw new ExNotShared();
            sid = SID.anchorOID2storeSID(soid.oid());
        }

        l.info("leave: {} {}", sid, ev._path);

        _tokenManager.inPseudoPause_(Cat.UNLIMITED, "sp-leave", () -> _factSP.create()
                .signInRemote()
                .leaveSharedFolder(BaseUtil.toPB(sid))
        );

        /**
         * Force ACL sync to make sure we do not report success to the Ritual caller before the
         * ACL changes propagate back to us.
         */
        _aclsync.syncToLocal_();
    }
}

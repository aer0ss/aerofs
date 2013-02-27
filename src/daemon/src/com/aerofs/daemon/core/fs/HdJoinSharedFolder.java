package com.aerofs.daemon.core.fs;

import com.aerofs.daemon.core.acl.ACLSynchronizer;
import com.aerofs.base.BaseParam.SP;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.sp.client.SPClientFactory;
import org.slf4j.Logger;

import com.aerofs.daemon.event.admin.EIJoinSharedFolder;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.Util;
import com.google.inject.Inject;

public class HdJoinSharedFolder extends AbstractHdIMC<EIJoinSharedFolder>
{
    private static final Logger l = Util.l(HdJoinSharedFolder.class);

    private final TC _tc;
    private final ACLSynchronizer _aclsync;

    @Inject
    public HdJoinSharedFolder(TC tc, ACLSynchronizer aclsync)
    {
        _tc = tc;
        _aclsync = aclsync;
    }

    @Override
    protected void handleThrows_(EIJoinSharedFolder ev, Prio prio) throws Exception
    {
        l.info("join: " + ev._sid);

        Token tk = _tc.acquireThrows_(Cat.UNLIMITED, "sp-join");
        TCB tcb = null;
        try {
            tcb = tk.pseudoPause_("sp-join");
            // join the shared folder through SP
            SPBlockingClient sp = SPClientFactory.newBlockingClient(SP.URL, Cfg.user());
            sp.signInRemote();
            sp.joinSharedFolder(ev._sid.toPB());
        } finally {
            if (tcb != null) tcb.pseudoResumed_();
            tk.reclaim_();
        }

        /**
         * Force ACL sync to make sure we do not report success to the Ritual caller before the
         * ACL changes propagate back to us.
         * The actual joining will be performed automatically on reception of updated ACLs
         */
        _aclsync.syncToLocal_();
    }
}

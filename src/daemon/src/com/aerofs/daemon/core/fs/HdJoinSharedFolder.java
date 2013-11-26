package com.aerofs.daemon.core.fs;

import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.acl.ACLSynchronizer;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import org.slf4j.Logger;

import com.aerofs.daemon.event.admin.EIJoinSharedFolder;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.event.Prio;
import com.google.inject.Inject;

import static com.aerofs.sp.client.InjectableSPBlockingClientFactory.newMutualAuthClientFactory;

public class HdJoinSharedFolder extends AbstractHdIMC<EIJoinSharedFolder>
{
    private static final Logger l = Loggers.getLogger(HdJoinSharedFolder.class);

    private final TokenManager _tokenManager;
    private final ACLSynchronizer _aclsync;

    @Inject
    public HdJoinSharedFolder(TokenManager tokenManager, ACLSynchronizer aclsync)
    {
        _tokenManager = tokenManager;
        _aclsync = aclsync;
    }

    @Override
    protected void handleThrows_(EIJoinSharedFolder ev, Prio prio) throws Exception
    {
        l.info("join: " + ev._sid);

        Token tk = _tokenManager.acquireThrows_(Cat.UNLIMITED, "sp-join");
        TCB tcb = null;
        try {
            tcb = tk.pseudoPause_("sp-join");
            // join the shared folder through SP
            newMutualAuthClientFactory().create()
                    .signInRemote()
                    .joinSharedFolder(ev._sid.toPB(), false);
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

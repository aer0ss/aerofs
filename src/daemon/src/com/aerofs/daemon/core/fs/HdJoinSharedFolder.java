package com.aerofs.daemon.core.fs;

import com.aerofs.daemon.core.acl.ACLSynchronizer;
import com.aerofs.daemon.core.acl.ExConcurrentACLUpdate;
import com.aerofs.lib.Param.SP;
import com.aerofs.lib.cfg.Cfg;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.sp.client.SPClientFactory;
import org.apache.log4j.Logger;

import com.aerofs.daemon.event.admin.EIJoinSharedFolder;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.daemon.lib.Prio;
import com.aerofs.lib.Util;
import com.google.inject.Inject;

public class HdJoinSharedFolder extends AbstractHdIMC<EIJoinSharedFolder>
{
    private static final Logger l = Util.l(HdJoinSharedFolder.class);

    private final ACLSynchronizer _aclsync;

    @Inject
    public HdJoinSharedFolder(ACLSynchronizer aclsync)
    {
        _aclsync = aclsync;
    }

    @Override
    protected void handleThrows_(EIJoinSharedFolder ev, Prio prio) throws Exception
    {
        assert !ev._code.isEmpty();

        l.info("join: " + ev._code);

        // join the shared folder through SP
        SPBlockingClient sp = SPClientFactory.newBlockingClient(SP.URL, Cfg.user());
        sp.signInRemote();
        sp.joinSharedFolder(ev._code);

        // keep ACL up to date so we can start syncing instantly after joining the store.
        // the actual joining will be performed automatically once ACL update propagates back to us
        try {
            _aclsync.syncToLocal_();
        } catch (ExConcurrentACLUpdate e) {
            l.warn("concurrent ACL update. ignore");
        }
    }
}

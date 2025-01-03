package com.aerofs.daemon.core.fs;

import com.aerofs.base.BaseUtil;
import com.aerofs.base.Loggers;
import com.aerofs.daemon.core.acl.ACLSynchronizer;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TokenManager;
import org.slf4j.Logger;

import com.aerofs.daemon.event.admin.EIJoinSharedFolder;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
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
    protected void handleThrows_(EIJoinSharedFolder ev) throws Exception
    {
        l.info("join: {}", ev._sid);

        _tokenManager.inPseudoPause_(Cat.UNLIMITED, "sp-join", () -> newMutualAuthClientFactory().create()
                .signInRemote()
                .joinSharedFolder(BaseUtil.toPB(ev._sid), false)
        );

        /**
         * Force ACL sync to make sure we do not report success to the Ritual caller before the
         * ACL changes propagate back to us.
         * The actual joining will be performed automatically on reception of updated ACLs
         */
        _aclsync.syncToLocal_();
    }
}

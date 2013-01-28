/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.admin;

import com.aerofs.base.BaseParam.SP;
import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.event.admin.EIListSharedFolderInvitations;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.event.Prio;
import com.aerofs.lib.cfg.CfgLocalUser;
import com.aerofs.sp.client.SPBlockingClient;
import com.aerofs.sp.client.SPClientFactory;
import com.google.inject.Inject;

public class HdListSharedFolderInvitations extends AbstractHdIMC<EIListSharedFolderInvitations>
{
    private final CfgLocalUser _localUser;
    private final TokenManager _tokenManager;

    @Inject
    public HdListSharedFolderInvitations(CfgLocalUser localUser, TokenManager tokenManager)
    {
        _localUser = localUser;
        _tokenManager = tokenManager;
    }

    @Override
    protected void handleThrows_(EIListSharedFolderInvitations ev, Prio prio) throws Exception
    {
        Token tk = _tokenManager.acquireThrows_(Cat.UNLIMITED, "list-invitations");
        TCB tcb = null;
        try {
            tcb = tk.pseudoPause_("list-invitations");
            SPBlockingClient sp = SPClientFactory.newBlockingClient(SP.URL, _localUser.get());
            sp.signInRemote();
            ev.setResult_(sp.listPendingFolderInvitations().getInvitationList());
        } finally {
            if (tcb != null) tcb.pseudoResumed_();
        }
    }
}

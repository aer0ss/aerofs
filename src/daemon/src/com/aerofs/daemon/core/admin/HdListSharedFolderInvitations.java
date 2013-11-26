/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.core.tc.Cat;
import com.aerofs.daemon.core.tc.TC.TCB;
import com.aerofs.daemon.core.tc.Token;
import com.aerofs.daemon.core.tc.TokenManager;
import com.aerofs.daemon.event.admin.EIListSharedFolderInvitations;
import com.aerofs.daemon.event.lib.imc.AbstractHdIMC;
import com.aerofs.lib.event.Prio;
import com.google.inject.Inject;

import static com.aerofs.sp.client.InjectableSPBlockingClientFactory.newMutualAuthClientFactory;

public class HdListSharedFolderInvitations extends AbstractHdIMC<EIListSharedFolderInvitations>
{
    private final TokenManager _tokenManager;

    @Inject
    public HdListSharedFolderInvitations(TokenManager tokenManager)
    {
        _tokenManager = tokenManager;
    }

    @Override
    protected void handleThrows_(EIListSharedFolderInvitations ev, Prio prio) throws Exception
    {
        Token tk = _tokenManager.acquireThrows_(Cat.UNLIMITED, "list-invitations");
        TCB tcb = null;
        try {
            tcb = tk.pseudoPause_("list-invitations");
            ev.setResult_(newMutualAuthClientFactory().create()
                    .signInRemote()
                    .listPendingFolderInvitations()
                    .getInvitationList());
        } finally {
            if (tcb != null) tcb.pseudoResumed_();
        }
    }
}

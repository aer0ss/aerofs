/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.core.admin;

import com.aerofs.daemon.core.tc.Cat;
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
        ev.setResult_(_tokenManager.inPseudoPause_(Cat.UNLIMITED, "list-invitations", () -> {
                    return newMutualAuthClientFactory().create()
                            .signInRemote()
                            .listPendingFolderInvitations()
                            .getInvitationList();
                }));
    }
}

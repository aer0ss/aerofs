/*
 * Copyright (c) Air Computing Inc., 2013.
 */

package com.aerofs.daemon.event.admin;

import com.aerofs.daemon.core.Core;
import com.aerofs.daemon.event.lib.imc.AbstractEBIMC;
import com.aerofs.proto.Common.PBFolderInvitation;

import java.util.Collection;

public class EIListSharedFolderInvitations extends AbstractEBIMC
{
    public Collection<PBFolderInvitation> _invitations;

    public EIListSharedFolderInvitations()
    {
        super(Core.imce());
    }

    public void setResult_(Collection<PBFolderInvitation> invitations)
    {
        _invitations = invitations;
    }
}

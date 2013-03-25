/*
 * Copyright (c) Air Computing Inc., 2012.
 */

package com.aerofs.sp.server.integration;

import com.aerofs.base.id.SID;
import com.aerofs.sp.server.email.InvitationEmailer;
import com.aerofs.sp.server.lib.organization.Organization;
import com.aerofs.sp.server.lib.user.User;

public class MockInvitationEmailerFactory extends InvitationEmailer.Factory
{
    @Override
    public InvitationEmailer createSignUpInvitationEmailer(User inviter, User invitee,
            String folderName, String note, String signUpCode)
    {
        return createNullEmailer();
    }

    @Override
    public InvitationEmailer createFolderInvitationEmailer(User inviter, User invitee,
            String folderName, String note, SID sid)
    {
        return createNullEmailer();
    }

    @Override
    public InvitationEmailer createOrganizationInvitationEmailer(User inviter, User invitee,
            Organization organization)
    {
        return createNullEmailer();
    }
}
